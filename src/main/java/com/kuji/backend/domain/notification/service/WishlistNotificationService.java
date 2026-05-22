package com.kuji.backend.domain.notification.service;

import com.kuji.backend.domain.kuji.entity.KujiBoard;
import com.kuji.backend.domain.kuji.entity.Wishlist;
import com.kuji.backend.domain.kuji.repository.WishlistRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관심상품(찜) 기반 일괄 알림 발송 서비스
 * - 대규모 발송 부하를 줄이기 위해 @Async 비동기 처리 사용
 * - 호출하는 쪽(예: 관리자 API)에서 원하는 시점에 메서드를 호출하면 됩니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistNotificationService {

    private final WishlistRepository wishlistRepository;
    private final NotificationService notificationService;

    /**
     * 관심상품 오픈 알림 (찜한 유저 전체 비동기 발송)
     * - 쿠지판이 ACTIVE 상태로 전환될 때 관리자가 호출
     */
    @Async
    @Transactional(readOnly = true)
    public void notifyWishlistOpen(KujiBoard board) {
        List<Wishlist> wishlists = wishlistRepository.findAllByKujiBoard(board);
        log.info("[관심상품 오픈 알림] 대상 {}명 발송 시작. boardId={}", wishlists.size(), board.getId());

        wishlists.forEach(wishlist -> {
            Member receiver = wishlist.getMember();
            notificationService.sendNotification(
                    receiver,
                    "관심 상품 오픈! 🔥",
                    String.format("찜해두신 [%s] 상품이 판매를 시작했습니다! 지금 확인해보세요.", board.getTitle()),
                    NotificationType.SYSTEM,
                    "WISHLIST_OPEN",
                    board.getId().toString()
            );
        });

        log.info("[관심상품 오픈 알림] 발송 완료. boardId={}", board.getId());
    }

    /**
     * 재입고 알림 (찜한 유저 전체 비동기 발송)
     * - 관리자가 상품 재입고 처리 시 호출
     */
    @Async
    @Transactional(readOnly = true)
    public void notifyRestock(KujiBoard board) {
        List<Wishlist> wishlists = wishlistRepository.findAllByKujiBoard(board);
        log.info("[재입고 알림] 대상 {}명 발송 시작. boardId={}", wishlists.size(), board.getId());

        wishlists.forEach(wishlist -> {
            Member receiver = wishlist.getMember();
            notificationService.sendNotification(
                    receiver,
                    "재입고 완료 안내 🔔",
                    String.format("기다리시던 [%s] 상품의 재고가 보충되었습니다!", board.getTitle()),
                    NotificationType.SYSTEM,
                    "RESTOCK",
                    board.getId().toString()
            );
        });

        log.info("[재입고 알림] 발송 완료. boardId={}", board.getId());
    }

    /**
     * 마감임박 알림 (찜한 유저 전체 비동기 발송)
     * - 잔여 수량이 특정 임계치 이하로 내려갈 때 관리자 또는 스케줄러가 호출
     */
    @Async
    @Transactional(readOnly = true)
    public void notifyClosingSoon(KujiBoard board, int remainQty) {
        List<Wishlist> wishlists = wishlistRepository.findAllByKujiBoard(board);
        log.info("[마감임박 알림] 대상 {}명 발송 시작. boardId={}, 잔여수량={}", wishlists.size(), board.getId(), remainQty);

        wishlists.forEach(wishlist -> {
            Member receiver = wishlist.getMember();
            notificationService.sendNotification(
                    receiver,
                    "품절 임박 경고! ⏳",
                    String.format("[%s] 상품의 잔여 수량이 %d개 남았습니다! 마감 전에 서두르세요.", board.getTitle(), remainQty),
                    NotificationType.SYSTEM,
                    "CLOSING_SOON",
                    board.getId().toString()
            );
        });

        log.info("[마감임박 알림] 발송 완료. boardId={}", board.getId());
    }
}
