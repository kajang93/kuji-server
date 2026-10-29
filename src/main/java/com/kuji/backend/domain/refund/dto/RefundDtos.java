// 환불 접수와 검토 API의 입력 및 실행 전 상태를 정의합니다.
package com.kuji.backend.domain.refund.dto;

import com.kuji.backend.domain.refund.entity.RefundRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class RefundDtos {
    private RefundDtos() {}
    public record Create(@NotNull RefundRequest.TargetType targetType, @NotNull @Positive Long targetId,
            @NotBlank @Size(min = 10, max = 1000) String reason) {}
    public record Review(@NotNull RefundRequest.Status status, @NotBlank @Size(min = 5, max = 1000) String reason) {}
    public record PageResponse<T>(List<T> content, int page, int size, boolean hasNext) {}
    public record Target(RefundRequest.TargetType targetType, Long targetId, String title, LocalDateTime occurredAt,
            Long targetAmount, String fulfillmentStatus, String notice) {}
    public record Response(Long id, Long memberId, String memberNickname, RefundRequest.TargetType targetType,
            Long targetId, String title, LocalDateTime sourceOccurredAt, Long targetAmount, Long refundAmount,
            String fulfillmentStatus, String reason, RefundRequest.Status status, String statusLabel,
            String reviewReason, LocalDateTime createdAt, LocalDateTime reviewedAt, Long reviewedBy,
            boolean executionPending, String statusNotice) {
        public static Response from(RefundRequest request) {
            String notice = request.getStatus() == RefundRequest.Status.REVIEW_APPROVED
                    ? "검토 승인은 환불 실행 대기이며 결제 취소·포인트 반환이 완료되지 않았습니다."
                    : "환불 접수와 검토 단계입니다. 환불 금액과 실제 처리 결과는 아직 확정되지 않았습니다.";
            return new Response(request.getId(), request.getMember().getId(), request.getMember().getNickname(),
                    request.getTargetType(), request.getTargetId(), request.getTitle(), request.getSourceOccurredAt(),
                    request.getTargetAmount(), null, request.getFulfillmentStatus(), request.getReason(),
                    request.getStatus(), request.getStatus().label(), request.getReviewReason(), request.getCreatedAt(),
                    request.getReviewedAt(), request.getReviewedBy(), request.getStatus() == RefundRequest.Status.REVIEW_APPROVED, notice);
        }
    }
}
