package com.kuji.backend.domain.kuji.service;

import com.kuji.backend.domain.kuji.dto.DrawHistoryResponse;
import com.kuji.backend.domain.kuji.dto.KujiDrawResponse;
import com.kuji.backend.domain.kuji.dto.KujiItemResponse;
import com.kuji.backend.domain.kuji.dto.RecentDrawResponse;
import com.kuji.backend.domain.kuji.entity.KujiBoard;
import com.kuji.backend.domain.kuji.entity.KujiItem;
import com.kuji.backend.domain.kuji.entity.DrawHistory;
import com.kuji.backend.domain.kuji.enums.BoardStatus;
import com.kuji.backend.domain.kuji.enums.DrawStatus;
import com.kuji.backend.domain.kuji.repository.KujiBoardRepository;
import com.kuji.backend.domain.kuji.repository.KujiItemRepository;
import com.kuji.backend.domain.kuji.repository.DrawHistoryRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.entity.PointHistory;
import com.kuji.backend.domain.member.enums.PointType;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.member.repository.PointHistoryRepository;
import com.kuji.backend.domain.notification.entity.NotificationType;
import com.kuji.backend.domain.notification.service.NotificationService;
import com.kuji.backend.domain.notification.service.WishlistNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KujiDrawService {

    private final KujiBoardRepository kujiBoardRepository;
    private final KujiItemRepository kujiItemRepository;
    private final DrawHistoryRepository drawHistoryRepository;
    private final MemberRepository memberRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final KujiItemService kujiItemService;
    private final NotificationService notificationService;
    private final WishlistNotificationService wishlistNotificationService;

    /**
     * 무작위 뽑기 실행 (비관적 락 적용)
     */
    @Transactional
    public KujiDrawResponse draw(Long memberId, Long boardId, int count) {
        // 1. 쿠지 판 조회 및 비관적 락 적용
        KujiBoard board = kujiBoardRepository.findByIdWithLock(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));

        if (board.getStatus() != BoardStatus.ACTIVE) {
            throw new IllegalStateException("현재 판매 중인 쿠지 판이 아닙니다.");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        List<KujiItem> winningItems = new ArrayList<>();
        List<DrawHistory> drawHistories = new ArrayList<>();
        Random random = new Random();

        // 2. 전체 상품 목록 한 번만 조회
        List<KujiItem> allItems = kujiItemRepository.findAllByKujiBoardIdOrderByGradeAsc(boardId);

        // 3. 요청한 갯수만큼 반복 추첨
        for (int i = 0; i < count; i++) {
            List<KujiItem> availableItems = allItems.stream()
                    .filter(it -> it.getRemainQty() > 0)
                    .collect(Collectors.toList());

            int totalRemain = availableItems.stream()
                    .mapToInt(KujiItem::getRemainQty)
                    .sum();

            if (totalRemain <= 0) {
                throw new IllegalStateException("남은 상품이 없습니다.");
            }

            // 가중치 기반 랜덤 선택
            int n = random.nextInt(totalRemain) + 1;
            int sum = 0;
            for (KujiItem item : availableItems) {
                sum += item.getRemainQty();
                if (n <= sum) {
                    // 당첨!
                    item.decreaseRemainQty();
                    winningItems.add(item);

                    // 당첨 이력 생성 (drawhistory 테이블 매핑)
                    DrawHistory history = DrawHistory.builder()
                            .status(DrawStatus.DRAWN)
                            .member(member)
                            .kujiBoard(board)
                            .kujiItem(item)
                            .build();
                    drawHistories.add(drawHistoryRepository.save(history));
                    break;
                }
            }
        }

        // 4. 포인트 적립 로직 (rewardRate를 1회당 고정 포인트로 사용)
        if (board.getRewardRate() != null && board.getRewardRate() > 0) {
            int rewardAmount = count * board.getRewardRate();
            if (rewardAmount > 0) {
                member.addPoint(rewardAmount);

                // 첫 번째 당첨 이력과 연결 (스키마 구조상 1:1 대응이 아닐 수 있으나 일단 연결)
                DrawHistory firstHistory = drawHistories.isEmpty() ? null : drawHistories.get(0);

                PointHistory pointHistory = PointHistory.builder()
                        .member(member)
                        .drawHistory(firstHistory)
                        .amount(rewardAmount)
                        .type(PointType.REWARD)
                        .description(
                                String.format("[%s] %d회 뽑기 적립 (%d%%)", board.getTitle(), count, board.getRewardRate()))
                        .appliedRewardRate(board.getRewardRate())
                        .build();
                pointHistoryRepository.save(pointHistory);
            }
        }

        // 5. 결과 반환
        List<KujiItemResponse> resultDtos = new java.util.ArrayList<>();
        for (DrawHistory history : drawHistories) {
            KujiItemResponse dto = kujiItemService.convertToResponse(history.getKujiItem());
            // Since DTO might be immutable, we assume it has @Data or we use a custom
            // setter
            // If it doesn't have a setter, we would need a new builder call
            dto.setDrawHistoryId(history.getId());
            resultDtos.add(dto);
        }

        int finalTotalRemain = allItems.stream()
                .mapToInt(KujiItem::getRemainQty)
                .sum();

        // 6. 당첨 알림 발송
        if (!winningItems.isEmpty()) {
            String mainPrizeName = winningItems.get(0).getName();
            String winBody = winningItems.size() > 1
                    ? String.format("축하합니다! [%s] 외 %d건의 상품에 당첨되셨습니다.", mainPrizeName, winningItems.size() - 1)
                    : String.format("축하합니다! [%s] 상품에 당첨되셨습니다.", mainPrizeName);
            notificationService.sendNotification(
                    member,
                    "개 당첨을 축하합니다!",
                    winBody,
                    NotificationType.SYSTEM,
                    "WINNING",
                    boardId.toString()
            );
        }

        // 7. 마감임박 알림 발송 트리거 (잔여 수량이 5개 이하로 떨어졌을 때, 그리고 당첨 전에는 5개 초과였을 때 딱 한 번만 발송)
        int initialTotalRemain = finalTotalRemain + count;
        if (initialTotalRemain > 5 && finalTotalRemain <= 5 && finalTotalRemain > 0) {
            wishlistNotificationService.notifyClosingSoon(board, finalTotalRemain);
        }

        return KujiDrawResponse.builder()
                .results(resultDtos)
                .totalRemaining(finalTotalRemain)
                .build();
    }

    /**
     * 내 당첨 내역(보관함) 조회
     */
    public List<DrawHistoryResponse> getMyDrawHistory(Long memberId) {
        List<DrawHistory> histories = drawHistoryRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);

        return histories.stream()
                .map(h -> DrawHistoryResponse.builder()
                        .id(h.getId())
                        .boardTitle(h.getKujiBoard().getTitle())
                        .grade(h.getKujiItem().getGrade())
                        .itemName(h.getKujiItem().getName())
                        .itemImageUrl(h.getKujiItem().getKujiItemImages().isEmpty() ? ""
                                : h.getKujiItem().getKujiItemImages().get(0).getImageUrl())
                        .status(h.getStatus())
                        .createdAt(h.getCreatedAt())
                        .shippingId(h.getShipping() != null ? h.getShipping().getId() : null)
                        .build())
                .toList();
    }

    /**
     * 전역 최근 당첨 내역 조회 (티커용)
     */
    public List<RecentDrawResponse> getRecentDrawHistory() {
        // 최신 20건 조회
        List<DrawHistory> histories = drawHistoryRepository.findTop20ByOrderByCreatedAtDesc();

        return histories.stream()
                .map(h -> RecentDrawResponse.builder()
                        .maskedNickname(maskNickname(h.getMember().getNickname()))
                        .boardTitle(h.getKujiBoard().getTitle())
                        .grade(h.getKujiItem().getGrade())
                        .itemName(h.getKujiItem().getName())
                        .createdAt(h.getCreatedAt())
                        .build())
                .toList();
    }

    /**
     * 닉네임 마스킹 (예: 홍길동 -> 홍** / ka**** -> ka***)
     */
    private String maskNickname(String nickname) {
        if (nickname == null || nickname.length() < 2) return "*";
        return nickname.substring(0, 2) + "*".repeat(Math.max(1, nickname.length() - 2));
    }
}
