// 판매 가능 여부와 재고를 확인한다. PG 승인 전 사전 확인은 별도 읽기 트랜잭션에서 수행해
// 쿠지 판 락 없이 검증하고, 본 트랜잭션의 영속성 컨텍스트에 오래된 재고 엔티티가 남지 않도록 한다.
package com.kuji.backend.domain.kuji.service;

import com.kuji.backend.domain.kuji.entity.KujiBoard;
import com.kuji.backend.domain.kuji.entity.KujiItem;
import com.kuji.backend.domain.kuji.enums.BoardStatus;
import com.kuji.backend.domain.kuji.repository.KujiBoardRepository;
import com.kuji.backend.domain.kuji.repository.KujiItemRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.BusinessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KujiDrawAvailability {

    private final KujiBoardRepository kujiBoardRepository;
    private final KujiItemRepository kujiItemRepository;

    /**
     * 락 없이 판매 가능 여부·재고를 확인하고 총 결제 금액을 돌려준다.
     * 결과는 참고용이며, 실제 추첨 직전에는 반드시 락을 잡고 다시 검증해야 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public int precheck(Long boardId, int count) {
        KujiBoard board = kujiBoardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));
        validateSaleAvailable(board);
        int total = calculateRequiredAmount(board, count);
        validateStock(kujiItemRepository.findAllByKujiBoardIdOrderByGradeAsc(boardId), count);
        return total;
    }

    public static void validateSaleAvailable(KujiBoard board) {
        if (board.isPreviewOnly() || board.getStatus() != BoardStatus.ACTIVE) {
            throw new IllegalStateException("현재 판매 중인 쿠지 판이 아닙니다.");
        }
        Member seller = board.getMember();
        if (seller.getBusinessInfo() == null || seller.getBusinessInfo().getStatus() != BusinessStatus.APPROVED) {
            throw new IllegalStateException("사업자 심사가 승인되지 않은 판매자의 상품입니다.");
        }
    }

    public static int calculateRequiredAmount(KujiBoard board, int count) {
        if (count < 1 || board.getPricePerDraw() == null || board.getPricePerDraw() < 1) {
            throw new IllegalArgumentException("뽑기 수량과 상품 가격은 양수여야 합니다.");
        }
        try {
            return Math.toIntExact(Math.multiplyExact(board.getPricePerDraw(), (long) count));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("결제 가능한 금액 범위를 초과했습니다.");
        }
    }

    public static void validateStock(List<KujiItem> items, int count) {
        LastPrizeRules.validateLastPrize(items);
        if (LastPrizeRules.remainingTickets(items) < count) {
            throw new IllegalStateException("요청한 수량보다 남은 일반 티켓이 부족합니다.");
        }
    }
}
