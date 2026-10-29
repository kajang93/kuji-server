// 배송 완료와 환불 검토 여부에 따라 실제 정산액과 과거 미대사 거래를 구분합니다.
package com.kuji.backend.domain.settlement.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository @RequiredArgsConstructor
public class SettlementQueryRepository {
    private final JdbcTemplate jdbc;
    private static final String REFUND_HOLD = "EXISTS (SELECT 1 FROM seller_sale_draw rhd JOIN refund_request rr "
            + "ON rr.target_type = 'DRAW_HISTORY' AND rr.target_id = rhd.draw_history_id "
            + "WHERE rhd.sale_id = s.id AND rr.status IN ('REQUESTED','UNDER_REVIEW','REVIEW_APPROVED'))";
    private static final String DELIVERED = "EXISTS (SELECT 1 FROM seller_sale_draw sd WHERE sd.sale_id = s.id) "
            + "AND NOT EXISTS (SELECT 1 FROM seller_sale_draw sd JOIN drawhistory d ON d.id = sd.draw_history_id "
            + "WHERE sd.sale_id = s.id AND d.status <> 'DELIVERED')";
    private static final String EVENTS = "WITH sale_events AS ("
            + "SELECT s.seller_id, s.completed_at::date AS sale_date, s.gross_amount AS amount, false AS legacy FROM seller_sale s WHERE s.payment_environment = 'LIVE' "
            + "UNION ALL SELECT b.member_id, h.created_at::date, h.amount::bigint, true FROM pointhistory h "
            + "JOIN drawhistory d ON d.id = h.draw_history_id JOIN kujiboard b ON b.id = d.kuji_board_id "
            + "WHERE h.type = 'USE' AND h.amount > 0 AND h.member_id = d.member_id "
            + "AND NOT EXISTS (SELECT 1 FROM seller_sale_draw sd WHERE sd.draw_history_id = h.draw_history_id) "
            + "UNION ALL SELECT b.member_id, (p.paid_at AT TIME ZONE 'Asia/Seoul')::date, p.amount::bigint, true FROM payment p "
            + "JOIN paymentsession ps ON ps.order_id = p.merchant_uid JOIN kujiboard b ON b.id = ps.board_id "
            + "WHERE p.status = 'PAID' AND ps.status = 'COMPLETED' AND ps.session_type = 'KUJI_DRAW' "
            + "AND ps.member_id = p.member_id AND ps.amount = p.amount AND p.amount > 0 "
            + "AND NOT EXISTS (SELECT 1 FROM seller_sale ss WHERE ss.source_key = 'PG:' || p.merchant_uid) "
            + "AND (SELECT count(*) FROM payment duplicate WHERE duplicate.merchant_uid = p.merchant_uid) = 1) ";

    public record SellerAmounts(long totalSales, long confirmedSales, long pendingSales, long platformFee,
            long pendingFee, long confirmedSettlement, long pendingSettlement, long refundHold,
            long legacySales, long legacyReviewCount, long legacyUnreconciledCount, long testSales, long unknownSales) {}
    public record DayAmount(LocalDate date, long amount) {}

    public SellerAmounts sellerAmounts(Long sellerId) {
        long[] posted = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN eligible THEN gross_amount ELSE 0 END),0), "
                + "COALESCE(SUM(CASE WHEN NOT eligible THEN gross_amount ELSE 0 END),0), "
                + "COALESCE(SUM(CASE WHEN eligible THEN fee_amount ELSE 0 END),0), "
                + "COALESCE(SUM(CASE WHEN NOT eligible THEN fee_amount ELSE 0 END),0), "
                + "COALESCE(SUM(CASE WHEN eligible THEN net_amount ELSE 0 END),0), "
                + "COALESCE(SUM(CASE WHEN NOT eligible THEN net_amount ELSE 0 END),0), "
                + "COALESCE(SUM(CASE WHEN refund_hold THEN net_amount ELSE 0 END),0) "
                + "FROM (SELECT s.*, (" + DELIVERED + ") AND NOT (" + REFUND_HOLD + ") AS eligible, "
                + REFUND_HOLD + " AS refund_hold FROM seller_sale s WHERE s.seller_id = ? AND s.payment_environment = 'LIVE') amounts",
                (rs, row) -> new long[]{rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getLong(6),rs.getLong(7)}, sellerId);
        long[] legacy = jdbc.queryForObject(EVENTS + "SELECT COALESCE(SUM(amount),0),COUNT(*) FROM sale_events WHERE seller_id = ? AND legacy",
                (rs,row) -> new long[]{rs.getLong(1),rs.getLong(2)},sellerId);
        Long unresolved = jdbc.queryForObject("SELECT COUNT(DISTINCT ps.order_id) FROM payment p "
                + "JOIN paymentsession ps ON ps.order_id = p.merchant_uid JOIN kujiboard b ON b.id = ps.board_id "
                + "WHERE b.member_id = ? AND ps.status = 'COMPLETED' AND ps.session_type = 'KUJI_DRAW' AND p.status = 'PAID' "
                + "AND NOT EXISTS (SELECT 1 FROM seller_sale s WHERE s.source_key = 'PG:' || p.merchant_uid) "
                + "AND (ps.member_id <> p.member_id OR ps.amount <> p.amount OR p.amount <= 0 "
                + "OR (SELECT count(*) FROM payment duplicate WHERE duplicate.merchant_uid = p.merchant_uid) <> 1)",Long.class,sellerId);
        long[] excluded = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN payment_environment = 'TEST' THEN gross_amount ELSE 0 END),0), "
                + "COALESCE(SUM(CASE WHEN payment_environment = 'UNKNOWN' THEN gross_amount ELSE 0 END),0) FROM seller_sale WHERE seller_id = ?",
                (rs,row) -> new long[]{rs.getLong(1),rs.getLong(2)},sellerId);
        return new SellerAmounts(posted[0]+posted[1]+legacy[0],posted[0],posted[1],posted[2],posted[3],posted[4],posted[5],posted[6],legacy[0],legacy[1]+unresolved,unresolved,excluded[0],excluded[1]);
    }
    public long totalSales() { return jdbc.queryForObject(EVENTS + "SELECT COALESCE(SUM(amount),0) FROM sale_events",Long.class); }
    public List<DayAmount> dailySales(Long sellerId, LocalDate from, LocalDate through) {
        String ownerFilter = sellerId == null ? "" : " AND seller_id = ?";
        String sql = EVENTS + "SELECT sale_date,SUM(amount) AS amount FROM sale_events WHERE sale_date >= ? AND sale_date <= ?"
                + ownerFilter + " GROUP BY sale_date ORDER BY sale_date";
        var mapper = (org.springframework.jdbc.core.RowMapper<DayAmount>) (rs,row) -> new DayAmount(rs.getObject("sale_date",LocalDate.class),rs.getLong("amount"));
        return sellerId == null ? jdbc.query(sql,mapper,from,through) : jdbc.query(sql,mapper,from,through,sellerId);
    }
}
