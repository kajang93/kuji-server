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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KujiBoardService {

    private final KujiBoardRepository kujiBoardRepository;
    private final KujiBoardImageRepository kujiBoardImageRepository;
    private final KujiItemRepository kujiItemRepository;
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

        KujiBoard kujiBoard = KujiBoard.builder()
                .title(request.getTitle())
                .pricePerDraw(request.getPricePerDraw())
                .status(request.getStatus())
                .member(member)
                .rewardRate(request.getRewardRate())
                .build();

        return kujiBoardRepository.save(kujiBoard).getId();
    }

    /**
     * 이미지 업로드 및 저장
     */
    @Transactional
    public void uploadImages(Long boardId, BoardImageType type, List<MultipartFile> files) {
        KujiBoard kujiBoard = kujiBoardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));

        // 기존 해당 타입의 이미지가 있다면 S3 및 DB에서 쓰레기 데이터 정리
        List<KujiBoardImage> existingImages = kujiBoardImageRepository.findAllByKujiBoardIdOrderBySequenceAsc(boardId).stream()
                .filter(img -> img.getImageType() == type)
                .collect(Collectors.toList());
        for (KujiBoardImage img : existingImages) {
            if (img.getImageUrl() != null && img.getImageUrl().startsWith("http")) {
                s3Service.deleteFile(img.getImageUrl());
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
        KujiBoard kujiBoard = kujiBoardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));
        com.kuji.backend.domain.kuji.enums.BoardStatus oldStatus = kujiBoard.getStatus();

        if (status == com.kuji.backend.domain.kuji.enums.BoardStatus.ACTIVE) {
            com.kuji.backend.domain.member.entity.Member member = kujiBoard.getMember();
            if (member.getBusinessInfo() == null || member.getBusinessInfo().getStatus() != com.kuji.backend.domain.member.enums.BusinessStatus.APPROVED) {
                throw new IllegalArgumentException("사업자 심사가 승인된 후에만 운영중(ACTIVE) 상태로 변경할 수 있습니다.");
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
        KujiBoard kujiBoard = kujiBoardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));
        kujiBoard.updateRewardRate(rewardRate);
    }

    /**
     * 전체 목록 조회 (찜 여부 포함)
     */
    public List<KujiBoardResponse> getAllBoards(Long memberId) {
        Member member = memberId != null ? memberRepository.findById(memberId).orElse(null) : null;
        
        return kujiBoardRepository.findByStatus(com.kuji.backend.domain.kuji.enums.BoardStatus.ACTIVE).stream()
                .map(board -> convertToResponse(board, member))
                .collect(Collectors.toList());
    }

    /**
     * 특정 판매자의 쿠지 판 목록 조회 (본인이 등록한 것만)
     */
    public List<KujiBoardResponse> getSellerBoards(Long sellerId) {
        Member member = memberRepository.findById(sellerId).orElse(null);
        return kujiBoardRepository.findByMemberId(sellerId).stream()
                .map(board -> convertToResponse(board, member))
                .collect(Collectors.toList());
    }

    /**
     * 쿠지 판 삭제
     */
    @Transactional
    public void deleteBoard(Long boardId, Long memberId) {
        KujiBoard board = kujiBoardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));
        
        if (!board.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("해당 상품을 삭제할 권한이 없습니다.");
        }

        // 1. S3에서 KujiBoard 이미지 파일 삭제
        List<KujiBoardImage> boardImages = kujiBoardImageRepository.findAllByKujiBoardIdOrderBySequenceAsc(boardId);
        for (KujiBoardImage img : boardImages) {
            if (img.getImageUrl() != null && img.getImageUrl().startsWith("http")) {
                s3Service.deleteFile(img.getImageUrl());
            }
        }

        // 2. S3에서 속한 KujiItem들의 이미지 파일 삭제
        List<KujiItem> items = kujiItemRepository.findAllByKujiBoardIdOrderByGradeAsc(boardId);
        for (KujiItem item : items) {
            for (com.kuji.backend.domain.kuji.entity.KujiItemImage itemImg : item.getKujiItemImages()) {
                if (itemImg.getImageUrl() != null && itemImg.getImageUrl().startsWith("http")) {
                    s3Service.deleteFile(itemImg.getImageUrl());
                }
            }
        }

        // 3. DB 데이터 삭제
        kujiItemRepository.deleteAllByKujiBoardId(boardId);
        kujiBoardImageRepository.deleteAllByKujiBoardId(boardId);
        wishlistRepository.deleteAllByKujiBoard(board);
        kujiBoardRepository.delete(board);
    }

    private KujiBoardResponse convertToResponse(KujiBoard board, Member member) {
        List<KujiBoardImage> images = kujiBoardImageRepository.findAllByKujiBoardIdOrderBySequenceAsc(board.getId());
        List<KujiItem> items = kujiItemRepository.findAllByKujiBoardIdOrderByGradeAsc(board.getId());

        int totalCount = items.stream()
                .mapToInt(item -> item.getTotalQty() != null ? item.getTotalQty() : 0)
                .sum();
        int remainCount = items.stream()
                .mapToInt(item -> item.getRemainQty() != null ? item.getRemainQty() : 0)
                .sum();
        int gradeCount = items.size();

        boolean isWished = false;
        if (member != null) {
            isWished = wishlistRepository.existsByMemberAndKujiBoard(member, board);
        }
        
        return KujiBoardResponse.builder()
                .id(board.getId())
                .title(board.getTitle())
                .pricePerDraw(board.getPricePerDraw())
                .status(board.getStatus())
                .rewardRate(board.getRewardRate())
                .createdAt(board.getCreatedAt())
                .totalCount(totalCount)
                .remainCount(remainCount)
                .gradeCount(gradeCount)
                .isWished(isWished)
                .images(images.stream()
                        .map(img -> KujiBoardResponse.KujiBoardImageResponse.builder()
                                .id(img.getId())
                                .imageUrl(img.getImageUrl())
                                .sequence(img.getSequence())
                                .imageType(img.getImageType() != null ? img.getImageType().name() : "THUMBNAIL")
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}
