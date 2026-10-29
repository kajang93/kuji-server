// 실제 본인 추첨과 승인된 포인트 충전만 환불 검토 대상으로 조회합니다.
package com.kuji.backend.domain.refund.repository;

import com.kuji.backend.domain.refund.dto.RefundDtos;
import com.kuji.backend.domain.refund.entity.RefundRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefundTargetRepository {
    private final JdbcTemplate jdbc;
    private static final String DRAWS = "SELECT d.id, b.title || ' · ' || i.name AS title, d.created_at AS occurred_at, "
            + "NULL::bigint AS target_amount, d.status AS fulfillment_status FROM drawhistory d "
            + "JOIN kujiboard b ON b.id = d.kuji_board_id JOIN kujiitem i ON i.id = d.kuji_item_id WHERE d.member_id = ?";
    private static final String CHARGES = "SELECT h.id, '포인트 충전' AS title, h.created_at AS occurred_at, "
            + "h.amount::bigint AS target_amount, p.status AS fulfillment_status FROM pointhistory h "
            + "JOIN payment p ON p.id = h.payment_id WHERE h.member_id = ? AND p.member_id = h.member_id "
            + "AND h.type = 'CHARGE' AND h.amount > 0 AND p.amount = h.amount AND p.status = 'PAID' "
            + "AND EXISTS (SELECT 1 FROM paymentsession s WHERE s.order_id = p.merchant_uid AND s.member_id = h.member_id "
            + "AND s.session_type = 'POINT_CHARGE' AND s.status = 'COMPLETED' AND s.amount = h.amount)";
    public List<RefundDtos.Target> list(Long memberId, RefundRequest.TargetType type, int limit, int offset) {
        return jdbc.query(base(type) + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?", mapper(type), memberId, limit, offset);
    }
    public Optional<RefundDtos.Target> findOwned(Long memberId, RefundRequest.TargetType type, Long id) {
        String idColumn = type == RefundRequest.TargetType.DRAW_HISTORY ? "d.id" : "h.id";
        return jdbc.query(base(type) + " AND " + idColumn + " = ?", mapper(type), memberId, id).stream().findFirst();
    }
    private String base(RefundRequest.TargetType type) { return type == RefundRequest.TargetType.DRAW_HISTORY ? DRAWS : CHARGES; }
    private RowMapper<RefundDtos.Target> mapper(RefundRequest.TargetType type) {
        return (rs, row) -> {
            Timestamp occurredAt = rs.getTimestamp("occurred_at");
            Long amount = rs.getObject("target_amount", Long.class);
            String notice = type == RefundRequest.TargetType.DRAW_HISTORY
                    ? "해당 당첨 내역을 기준으로 검토합니다. 주문 전체 금액과 환불 가능 금액은 별도 확인이 필요합니다."
                    : "표시 금액은 원래 유상 충전액입니다. 보너스와 사용 내역을 확인한 뒤 환불 가능 금액을 검토합니다.";
            return new RefundDtos.Target(type, rs.getLong("id"), rs.getString("title"),
                    occurredAt == null ? null : occurredAt.toLocalDateTime(), amount, rs.getString("fulfillment_status"), notice);
        };
    }
}
