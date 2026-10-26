package com.kuji.backend.domain.kuji.service;

import com.kuji.backend.domain.kuji.dto.KujiBoardCreateRequest;
import com.kuji.backend.domain.kuji.dto.KujiBoardResponse;
import com.kuji.backend.domain.kuji.entity.KujiBoard;
import com.kuji.backend.domain.kuji.entity.KujiBoardImage;
import com.kuji.backend.domain.kuji.enums.BoardImageType;
import com.kuji.backend.domain.kuji.repository.KujiBoardImageRepository;
import com.kuji.backend.domain.kuji.repository.KujiBoardRepository;
import com.kuji.backend.domain.kuji.repository.KujiItemRepository;
import com.kuji.backend.domain.kuji.repository.WishlistRepository; // 추가
import com.kuji.backend.domain.kuji.entity.KujiItem;
import com.kuji.backend.domain.notification.service.WishlistNotificationService;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.global.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.kuji.backend.domain.member.enums.RoleType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KujiBoardService {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final KujiBoardRepository kujiBoardRepository;
    private final KujiBoardImageRepository kujiBoardImageRepository;
    private final KujiItemRepository kujiItemRepository;
    private final com.kuji.backend.domain.kuji.repository.DrawHistoryRepository drawHistoryRepository;
    private final MemberRepository memberRepository;
    private final WishlistRepository wishlistRepository; // 추가
    private final S3Service s3Service;
    private final WishlistNotificationService wishlistNotificationService;

    /**
     * 쿠지 판 생성
     */
    @Transactional
    public Long createBoard(Long memberId, KujiBoardCreateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        if (member.getRole() != RoleType.BIZ && member.getRole() != RoleType.ADMIN) {
            throw new AccessDeniedException("판매자만 쿠지 판을 등록할 수 있습니다.");
        }

        validateTitle(request.getTitle());
        if (request.getPricePerDraw() == null || request.getPricePerDraw() < 1 || request.getPricePerDraw() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("쿠지 가격은 결제 가능한 범위의 양수여야 합니다.");
        }
        validateRewardRate(request.getRewardRate());
        if (request.getStatus() != null && request.getStatus() != com.kuji.backend.domain.kuji.enums.BoardStatus.PREPARING) {
            throw new IllegalArgumentException("상품은 준비 상태로 등록한 후 구성을 확인하고 판매를 시작해 주세요.");
        }

        KujiBoard kujiBoard = KujiBoard.builder()
                .title(request.getTitle().trim())
                .pricePerDraw(request.getPricePerDraw())
                .status(com.kuji.backend.domain.kuji.enums.BoardStatus.PREPARING)
                .member(member)
                .rewardRate(request.getRewardRate())
                .build();

        return kujiBoardRepository.save(kujiBoard).getId();
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank() || title.length() > 100) throw new IllegalArgumentException("시리즈명은 1자 이상 100자 이내로 입력해 주세요.");
    }

    private void validateRewardRate(Integer rewardRate) {
        if (rewardRate != null && (rewardRate < 0 || rewardRate > 100)) throw new IllegalArgumentException("적립률은 0부터 100 사이여야 합니다.");
    }

    @Transactional
    public KujiBoard lockEditableBoard(Long boardId) {
        KujiBoard board = kujiBoardRepository.findByIdWithLock(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));
        entityManager.refresh(board, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (board.getStatus() == com.kuji.backend.domain.kuji.enums.BoardStatus.ACTIVE || drawHistoryRepository.existsByKujiBoardId(boardId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "판매 중이거나 판매 이력이 있는 쿠지 판의 구성은 변경할 수 없습니다.");
        }
        return board;
    }

    @Transactional
    public void updateTitle(Long boardId, String title) {
        validateTitle(title);
        lockEditableBoard(boardId).updateTitle(title.trim());
    }

    public void validateManageAccess(Long boardId, Long memberId) {
        KujiBoard board = kujiBoardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AccessDeniedException("회원을 찾을 수 없습니다."));
        if (member.getRole() == RoleType.ADMIN) {
            return;
        }
        if (member.getRole() != RoleType.BIZ || !board.getMember().getId().equals(memberId)) {
            throw new AccessDeniedException("해당 쿠지 판을 관리할 권한이 없습니다.");
        }
    }

    /**
     * 이미지 업로드 및 저장
     */
    @Transactional
    public void uploadImages(Long boardId, BoardImageType type, List<MultipartFile> files) {
        KujiBoard kujiBoard = lockEditableBoard(boardId);
        if (type == null || files == null || files.isEmpty() || files.size() > 10 || files.stream().anyMatch(f -> f == null || f.isEmpty())) {
            throw new IllegalArgumentException("업로드할 상품 이미지를 확인해 주세요.");
        }

        // 기존 해당 타입의 이미지가 있다면 S3 및 DB에서 쓰레기 데이터 정리
        List<KujiBoardImage> existingImages = kujiBoardImageRepository.findAllByKujiBoardIdOrderBySequenceAsc(boardId).stream()
                .filter(img -> img.getImageType() == type)
                .collect(Collectors.toList());
        for (KujiBoardImage img : existingImages) {
            if (img.getImageUrl() != null && img.getImageUrl().startsWith("http")) {
                deleteImageAfterCommit(img.getImageUrl());
            }
        }
        kujiBoardImageRepository.deleteAll(existingImages);

        for (int i = 0; i < files.size(); i++) {
            String imageUrl = s3Service.uploadFile("boards", files.get(i));
            KujiBoardImage image = KujiBoardImage.builder()
                    .kujiBoard(kujiBoard)
                    .imageUrl(imageUrl)
                    .sequence(i + 1)
                    .imageType(type)
                    .build();
            kujiBoardImageRepository.save(image);
        }
    }

    /**
     * 상태 변경
     */
    @Transactional
    public void updateBoardStatus(Long boardId, com.kuji.backend.domain.kuji.enums.BoardStatus status) {
        if (status == null) throw new IllegalArgumentException("운영 상태를 선택해 주세요.");
        KujiBoard kujiBoard = kujiBoardRepository.findByIdWithLock(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));
        entityManager.refresh(kujiBoard, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        com.kuji.backend.domain.kuji.enums.BoardStatus oldStatus = kujiBoard.getStatus();

        if (status == com.kuji.backend.domain.kuji.enums.BoardStatus.ACTIVE) {
            com.kuji.backend.domain.member.entity.Member member = kujiBoard.getMember();
            if (member.getBusinessInfo() == null || member.getBusinessInfo().getStatus() != com.kuji.backend.domain.member.enums.BusinessStatus.APPROVED) {
                throw new IllegalArgumentException("사업자 심사가 승인된 후에만 운영중(ACTIVE) 상태로 변경할 수 있습니다.");
            }
            List<KujiItem> items = kujiItemRepository.findAllByKujiBoardIdOrderByGradeAsc(boardId);
            if (kujiBoard.getPricePerDraw() == null || kujiBoard.getPricePerDraw() < 1 ||
                    kujiBoard.getPricePerDraw() > Integer.MAX_VALUE || items.isEmpty() ||
                    items.stream().noneMatch(item -> item.getRemainQty() != null && item.getRemainQty() > 0) ||
                    items.stream().anyMatch(item -> item.getName() == null || item.getName().isBlank() || item.getTotalQty() == null || item.getTotalQty() < 1) ||
                    kujiBoardImageRepository.findAllByKujiBoardIdOrderBySequenceAsc(boardId).stream().noneMatch(img -> img.getImageType() == BoardImageType.THUMBNAIL)) {
                throw new IllegalArgumentException("가격, 상품명, 재고와 대표 이미지를 등록한 후 판매를 시작해 주세요.");
            }
        }

        kujiBoard.updateStatus(status);

        if (status == com.kuji.backend.domain.kuji.enums.BoardStatus.ACTIVE) {
            if (oldStatus == com.kuji.backend.domain.kuji.enums.BoardStatus.FINISHED) {
                wishlistNotificationService.notifyRestock(kujiBoard);
            } else if (oldStatus == com.kuji.backend.domain.kuji.enums.BoardStatus.PREPARING) {
                wishlistNotificationService.notifyWishlistOpen(kujiBoard);
            }
        }
    }

    /**
     * 쿠지 판 적립 포인트 변경
     */
    @Transactional
    public void updateRewardRate(Long boardId, Integer rewardRate) {
        validateRewardRate(rewardRate);
        lockEditableBoard(boardId).updateRewardRate(rewardRate);
    }

    /**
     * 전체 목록 조회 (찜 여부 포함)
     */
    public List<KujiBoardResponse> getAllBoards(Long memberId) {
        List<KujiBoard> boards = kujiBoardRepository.findByStatus(com.kuji.backend.domain.kuji.enums.BoardStatus.ACTIVE);
        return convertToResponses(boards, memberId);
    }

    /**
     * 특정 판매자의 쿠지 판 목록 조회 (본인이 등록한 것만)
     */
    public List<KujiBoardResponse> getSellerBoards(Long sellerId) {
        return convertToResponses(kujiBoardRepository.findByMemberId(sellerId), sellerId);
    }

    /**
     * 쿠지 판 삭제
     */
    @Transactional
    public void deleteBoard(Long boardId, Long memberId) {
        validateManageAccess(boardId, memberId);
        KujiBoard board = lockEditableBoard(boardId);

        // 1. S3에서 KujiBoard 이미지 파일 삭제
        List<KujiBoardImage> boardImages = kujiBoardImageRepository.findAllByKujiBoardIdOrderBySequenceAsc(boardId);
        for (KujiBoardImage img : boardImages) {
            if (img.getImageUrl() != null && img.getImageUrl().startsWith("http")) {
                deleteImageAfterCommit(img.getImageUrl());
            }
        }

        // 2. S3에서 속한 KujiItem들의 이미지 파일 삭제
        List<KujiItem> items = kujiItemRepository.findAllByKujiBoardIdOrderByGradeAsc(boardId);
        for (KujiItem item : items) {
            for (com.kuji.backend.domain.kuji.entity.KujiItemImage itemImg : item.getKujiItemImages()) {
                if (itemImg.getImageUrl() != null && itemImg.getImageUrl().startsWith("http")) {
                    deleteImageAfterCommit(itemImg.getImageUrl());
                }
            }
        }

        // 3. DB 데이터 삭제
        kujiItemRepository.deleteAllByKujiBoardId(boardId);
        kujiBoardImageRepository.deleteAllByKujiBoardId(boardId);
        wishlistRepository.deleteAllByKujiBoard(board);
        kujiBoardRepository.delete(board);
    }

    private void deleteImageAfterCommit(String url) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { s3Service.deleteFile(url); }
                });
    }

    /**
     * 목록 응답 일괄 변환.
     * 기존에는 보드마다 이미지/아이템/찜 3쿼리(1+3N)를 실행했으나,
     * 재고 집계·이미지·찜 목록을 각각 1쿼리로 묶어 보드 수와 무관하게 고정 쿼리 수로 처리한다.
     */
    private List<KujiBoardResponse> convertToResponses(List<KujiBoard> boards, Long memberId) {
        if (boards.isEmpty()) {
            return List.of();
        }

        List<Long> boardIds = boards.stream().map(KujiBoard::getId).toList();

        java.util.Map<Long, KujiItemRepository.BoardStockSummary> stockMap =
                kujiItemRepository.summarizeByBoardIds(boardIds).stream()
                        .collect(Collectors.toMap(KujiItemRepository.BoardStockSummary::getBoardId, s -> s));

        java.util.Map<Long, List<KujiBoardImage>> imageMap =
                kujiBoardImageRepository.findAllByKujiBoardIdInOrderBySequenceAsc(boardIds).stream()
                        .collect(Collectors.groupingBy(img -> img.getKujiBoard().getId()));

        java.util.Set<Long> wishedBoardIds = memberId != null
                ? new java.util.HashSet<>(wishlistRepository.findKujiBoardIdsByMemberId(memberId))
                : java.util.Set.of();

        return boards.stream()
                .map(board -> {
                    KujiItemRepository.BoardStockSummary stock = stockMap.get(board.getId());
                    List<KujiBoardImage> images = imageMap.getOrDefault(board.getId(), List.of());

                    return KujiBoardResponse.builder()
                            .id(board.getId())
                            .title(board.getTitle())
                            .pricePerDraw(board.getPricePerDraw())
                            .status(board.getStatus())
                            .rewardRate(board.getRewardRate())
                            .createdAt(board.getCreatedAt())
                            .totalCount(stock != null ? stock.getTotalQty().intValue() : 0)
                            .remainCount(stock != null ? stock.getRemainQty().intValue() : 0)
                            .gradeCount(stock != null ? stock.getGradeCount().intValue() : 0)
                            .isWished(wishedBoardIds.contains(board.getId()))
                            .images(images.stream()
                                    .map(img -> KujiBoardResponse.KujiBoardImageResponse.builder()
                                            .id(img.getId())
                                            .imageUrl(img.getImageUrl())
                                            .sequence(img.getSequence())
                                            .imageType(img.getImageType() != null ? img.getImageType().name() : "THUMBNAIL")
                                            .build())
                                    .collect(Collectors.toList()))
                            .build();
                })
                .collect(Collectors.toList());
    }
}
