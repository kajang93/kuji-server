// 정산 원장 선택과 실제 송금 확인 후 수동 지급 기록의 공개 계약을 정의한다.
package com.kuji.backend.domain.settlement.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SettlementDtos {
    private SettlementDtos() {}
    public record PageResponse<T>(List<T> content, int page, int size, boolean hasNext) {}
    public record Seller(Long sellerId, String sellerName, long eligibleSaleCount, long eligibleAmount, long paidAmount) {}
    public record Sale(Long id, String boardTitle, LocalDateTime completedAt, int purchasedCount,
            long grossAmount, long feeAmount, long netAmount, String paymentEnvironment,
            boolean delivered, boolean refundHold, boolean paid, boolean eligible, Long payoutId, String statusReason) {}
    public record Selection(@NotEmpty @Size(max = 100) List<@NotNull @Positive Long> saleIds) {}
    public record Preview(Long sellerId, List<Long> saleIds, long grossAmount, long feeAmount, long netAmount,
            int saleCount, boolean manualTransfer, String notice) {}
    public record CreatePayout(@NotNull UUID requestId,
            @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> saleIds,
            @NotNull @Positive Long expectedAmount, @NotNull LocalDate paidDate,
            @NotBlank @Size(max = 200) String transferReference,
            @NotBlank @Size(max = 500) String evidenceReference,
            @NotNull @AssertTrue Boolean transferConfirmed) {}
    public record Payout(Long id, Long sellerId, String sellerName, long amount, LocalDate paidDate,
            String transferReference, String evidenceReference, UUID requestId, OffsetDateTime recordedAt,
            boolean manualTransfer, String notice, List<Sale> sales) {}
}
