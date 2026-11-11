// 적격 원장 조회와 판매자·구매자 잠금 및 변경 불가 수동 지급 기록 저장을 담당한다.
package com.kuji.backend.domain.settlement.repository;

import com.kuji.backend.domain.settlement.dto.SettlementDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Date;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class SellerPayoutRepository {
    private final JdbcTemplate jdbc;
    public static final String RECORD_NOTICE = "은행 송금 기능이 아니라 실제 송금 확인 후 수동으로 남긴 지급 기록입니다. 기록 삭제·금액 변경·배분 해제는 지원하지 않으며 오입력은 원본 증빙과 함께 별도 대사가 필요합니다.";
    private static final String SALE_COLUMNS = "SELECT s.id,b.title AS board_title,s.completed_at,s.purchased_count,s.gross_amount,s.fee_amount,s.net_amount,s.payment_environment,("
            + SettlementQueryRepository.DELIVERED + ") AS delivered,(" + SettlementQueryRepository.REFUND_HOLD
            + ") AS refund_hold,a.payout_id FROM seller_sale s JOIN kujiboard b ON b.id=s.board_id "
            + "LEFT JOIN seller_payout_allocation a ON a.sale_id=s.id ";
    private static final RowMapper<SettlementDtos.Sale> SALE_MAPPER = (rs, row) -> {
        Long payoutId = rs.getObject("payout_id", Long.class);
        boolean delivered = rs.getBoolean("delivered");
        boolean hold = rs.getBoolean("refund_hold");
        String environment = rs.getString("payment_environment");
        long net = rs.getLong("net_amount");
        boolean eligible = "LIVE".equals(environment) && delivered && !hold && payoutId == null && net > 0;
        String reason = payoutId != null ? "지급 기록 있음" : !"LIVE".equals(environment) ? "실제 결제 환경이 확인된 거래만 지급 가능"
                : !delivered ? "주문 전체 배송 완료 대기" : hold ? "환불 검토로 지급 보류" : net <= 0 ? "지급 대상 금액 없음" : "지급 기록 가능";
        return new SettlementDtos.Sale(rs.getLong("id"), rs.getString("board_title"),
                rs.getTimestamp("completed_at").toLocalDateTime(), rs.getInt("purchased_count"),
                rs.getLong("gross_amount"), rs.getLong("fee_amount"), net, environment,
                delivered, hold, payoutId != null, eligible, payoutId, reason);
    };
    public record StoredPayout(SettlementDtos.Payout value, String fingerprint) {}

    public List<SettlementDtos.Seller> sellers(int page, int size) {
        String sql = "WITH eligible AS (SELECT s.seller_id,COUNT(*) AS sale_count,SUM(s.net_amount) AS amount FROM seller_sale s WHERE "
                + SettlementQueryRepository.PAYOUT_ELIGIBLE + " AND NOT EXISTS (SELECT 1 FROM seller_payout_allocation a WHERE a.sale_id=s.id) GROUP BY s.seller_id), "
                + "paid AS (SELECT seller_id,SUM(amount) AS amount FROM seller_payout GROUP BY seller_id), "
                + "sellers AS (SELECT seller_id FROM seller_sale UNION SELECT seller_id FROM seller_payout) "
                + "SELECT m.id,m.nickname,COALESCE(e.sale_count,0),COALESCE(e.amount,0),COALESCE(p.amount,0) FROM sellers s "
                + "JOIN member m ON m.id=s.seller_id LEFT JOIN eligible e ON e.seller_id=m.id LEFT JOIN paid p ON p.seller_id=m.id "
                + "ORDER BY m.id LIMIT ? OFFSET ?";
        return jdbc.query(sql, (rs, row) -> new SettlementDtos.Seller(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)), size + 1, page * size);
    }
    public List<SettlementDtos.Sale> sales(Long sellerId, int page, int size) {
        return jdbc.query(SALE_COLUMNS + "WHERE s.seller_id=? ORDER BY s.id DESC LIMIT ? OFFSET ?", SALE_MAPPER, sellerId, size + 1, page * size);
    }
    public List<SettlementDtos.Sale> selected(Long sellerId, List<Long> ids) {
        return jdbc.query(SALE_COLUMNS + "WHERE s.seller_id=? AND s.id IN (" + placeholders(ids.size()) + ") ORDER BY s.id", SALE_MAPPER, arguments(sellerId, ids));
    }
    public List<Long> buyerIds(Long sellerId, List<Long> ids) {
        return jdbc.queryForList("SELECT buyer_id FROM seller_sale WHERE seller_id=? AND id IN (" + placeholders(ids.size()) + ") ORDER BY id", Long.class, arguments(sellerId, ids));
    }
    public void lockRequest(UUID requestId) {
        jdbc.execute("SET LOCAL lock_timeout='5s'");
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class, requestId.toString());
    }
    public void lockMembers(SortedSet<Long> ids) {
        jdbc.queryForList("SELECT id FROM member WHERE id IN (" + placeholders(ids.size()) + ") ORDER BY id FOR UPDATE", Long.class, ids.toArray());
    }
    public void lockSalesAndDraws(Long sellerId, List<Long> ids) {
        jdbc.queryForList("SELECT id FROM seller_sale WHERE seller_id=? AND id IN (" + placeholders(ids.size()) + ") ORDER BY id FOR UPDATE", Long.class, arguments(sellerId, ids));
        jdbc.queryForList("SELECT d.id FROM drawhistory d JOIN seller_sale_draw sd ON sd.draw_history_id=d.id "
                + "WHERE sd.sale_id IN (" + placeholders(ids.size()) + ") ORDER BY d.id FOR UPDATE OF d", Long.class, ids.toArray());
    }
    public Optional<StoredPayout> byRequest(UUID requestId) {
        return header("p.request_id=?", requestId).map(this::withSales);
    }
    public Optional<StoredPayout> byId(Long id, Long sellerId) {
        return (sellerId == null ? header("p.id=?", id) : header("p.id=? AND p.seller_id=?", id, sellerId)).map(this::withSales);
    }
    public List<SettlementDtos.Payout> payouts(Long sellerId, int page, int size) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM seller_payout WHERE seller_id=? ORDER BY recorded_at DESC,id DESC LIMIT ? OFFSET ?", Long.class, sellerId, size + 1, page * size);
        return ids.stream().map(id -> byId(id, sellerId).orElseThrow().value()).toList();
    }
    public long insert(Long sellerId, Long adminId, SettlementDtos.CreatePayout input, String fingerprint) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO seller_payout(seller_id,amount,paid_date,transfer_reference,evidence_reference,request_id,request_fingerprint,recorded_by) VALUES (?,?,?,?,?,?,?,?)", new String[]{"id"});
            statement.setLong(1, sellerId); statement.setLong(2, input.expectedAmount());
            statement.setDate(3, Date.valueOf(input.paidDate())); statement.setString(4, input.transferReference());
            statement.setString(5, input.evidenceReference()); statement.setObject(6, input.requestId());
            statement.setString(7, fingerprint); statement.setLong(8, adminId);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    public void allocate(Long payoutId, SettlementDtos.Sale sale) {
        jdbc.update("INSERT INTO seller_payout_allocation(payout_id,sale_id,amount) VALUES (?,?,?)", payoutId, sale.id(), sale.netAmount());
    }
    private Optional<StoredPayout> header(String condition, Object... parameters) {
        return jdbc.query("SELECT p.*,m.nickname FROM seller_payout p JOIN member m ON m.id=p.seller_id WHERE " + condition,
                (rs, row) -> new StoredPayout(new SettlementDtos.Payout(rs.getLong("id"), rs.getLong("seller_id"), rs.getString("nickname"),
                        rs.getLong("amount"), rs.getDate("paid_date").toLocalDate(), rs.getString("transfer_reference"), rs.getString("evidence_reference"),
                        rs.getObject("request_id", UUID.class), rs.getObject("recorded_at", OffsetDateTime.class), true, RECORD_NOTICE, List.of()),
                        rs.getString("request_fingerprint")), parameters).stream().findFirst();
    }
    private StoredPayout withSales(StoredPayout stored) {
        var p = stored.value();
        var sales = jdbc.query(SALE_COLUMNS + "WHERE a.payout_id=? ORDER BY s.id", SALE_MAPPER, p.id());
        return new StoredPayout(new SettlementDtos.Payout(p.id(), p.sellerId(), p.sellerName(), p.amount(), p.paidDate(),
                p.transferReference(), p.evidenceReference(), p.requestId(), p.recordedAt(), true, RECORD_NOTICE, sales), stored.fingerprint());
    }
    private static Object[] arguments(Long sellerId, List<Long> ids) {
        var parameters = new ArrayList<Object>(); parameters.add(sellerId); parameters.addAll(ids); return parameters.toArray();
    }
    private static String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }
}
