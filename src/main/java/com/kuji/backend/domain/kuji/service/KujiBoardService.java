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
     * 전체 목록 조회 (찜 여부 포함)
     */
    public List<KujiBoardResponse> getAllBoards(Long memberId) {
        Member member = memberId != null ? memberRepository.findById(memberId).orElse(null) : null;
        
        return kujiBoardRepository.findAll().stream()
                .map(board -> convertToResponse(board, member))
                .collect(Collectors.toList());
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
