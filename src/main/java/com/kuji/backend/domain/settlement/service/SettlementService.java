// 구매 트랜잭션 안에서 실제 매출과 거래 당시 수수료를 확정 기록합니다.
package com.kuji.backend.domain.settlement.service;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.kuji.entity.KujiBoard;
import com.kuji.backend.domain.kuji.entity.DrawHistory;
import com.kuji.backend.domain.payment.entity.Payment;
import com.kuji.backend.domain.payment.enums.PaymentStatus;
import com.kuji.backend.domain.settlement.entity.SellerSale;
import com.kuji.backend.domain.settlement.repository.SellerSaleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;

@Service
public class SettlementService {
    private final SellerSaleRepository sales;
    private final SellerSale.PaymentEnvironment paymentEnvironment;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    public SettlementService(SellerSaleRepository sales, @Value("${toss.secret-key:}") String secretKey) {
        this.sales = sales;
        this.paymentEnvironment = secretKey != null && secretKey.startsWith("live_sk_") ? SellerSale.PaymentEnvironment.LIVE
                : secretKey != null && secretKey.startsWith("test_sk_") ? SellerSale.PaymentEnvironment.TEST
                : SellerSale.PaymentEnvironment.UNKNOWN;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSale(Member buyer, KujiBoard board, Payment payment, int count,
            int grossAmount, int pointsUsed, List<DrawHistory> histories) {
        if (buyer == null || buyer.getId() == null || board == null || board.getId() == null
                || board.getMember() == null || board.getMember().getBusinessInfo() == null
                || count < 1 || grossAmount < 1 || pointsUsed < 0 || histories == null || histories.size() < count) {
            throw new IllegalArgumentException("실제 구매 거래와 추첨 내역을 확인할 수 없습니다.");
        }
        var ids = new HashSet<Long>();
        for (DrawHistory history : histories) {
            if (history == null || history.getId() == null || !ids.add(history.getId())
                    || !buyer.getId().equals(history.getMember().getId())
                    || !board.getId().equals(history.getKujiBoard().getId())) {
                throw new IllegalArgumentException("구매 거래와 추첨 내역의 소유자 또는 상품이 일치하지 않습니다.");
            }
        }
        long pgAmount = 0;
        String sourceKey = "POINT:" + histories.getFirst().getId();
        if (payment != null) {
            if (payment.getId() == null || payment.getStatus() != PaymentStatus.PAID
                    || !buyer.getId().equals(payment.getMember().getId()) || payment.getMerchantUid() == null
                    || payment.getMerchantUid().isBlank() || payment.getMerchantUid().length() > 100) {
                throw new IllegalArgumentException("구매 결제 영수증을 확인할 수 없습니다.");
            }
            try { pgAmount = payment.getAmount().longValueExact(); }
            catch (ArithmeticException | NullPointerException e) { throw new IllegalArgumentException("실제 결제 금액이 올바르지 않습니다."); }
            if (pgAmount < 1) throw new IllegalArgumentException("실제 결제 금액이 올바르지 않습니다.");
            sourceKey = "PG:" + payment.getMerchantUid();
        }
        if ((long) grossAmount != pgAmount + pointsUsed) {
            throw new IllegalArgumentException("구매대금과 현금·포인트 결제 합계가 일치하지 않습니다.");
        }
        var business = board.getMember().getBusinessInfo();
        Integer feeRate = business.getBaseFeeRate();
        if (feeRate == null || feeRate < 0 || feeRate > 100) throw new IllegalArgumentException("판매 수수료 설정을 확인해주세요.");
        ZonedDateTime now = ZonedDateTime.now(KOREA);
        if (business.getPromotionEndAt() != null && now.isBefore(business.getPromotionEndAt())) feeRate = 0;
        sales.saveAndFlush(new SellerSale(buyer, board, payment, sourceKey, paymentEnvironment, count, grossAmount, pgAmount, pointsUsed,
                feeRate, now.toLocalDateTime(), histories));
    }
}
