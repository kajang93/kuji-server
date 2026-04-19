package com.kuji.backend.domain.kuji.service;

import com.kuji.backend.domain.kuji.dto.KujiBoardCreateRequest;
import com.kuji.backend.domain.kuji.dto.KujiBoardResponse;
import com.kuji.backend.domain.kuji.entity.KujiBoard;
import com.kuji.backend.domain.kuji.entity.KujiBoardImage;
import com.kuji.backend.domain.kuji.enums.BoardImageType;
import com.kuji.backend.domain.kuji.repository.KujiBoardImageRepository;
import com.kuji.backend.domain.kuji.repository.KujiBoardRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.global.service.FileService;
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
    private final MemberRepository memberRepository;
    private final FileService fileService;

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

        for (int i = 0; i < files.size(); i++) {
            String imageUrl = fileService.saveFile("boards", files.get(i));
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
     * 전체 목록 조회
     */
    public List<KujiBoardResponse> getAllBoards() {
        return kujiBoardRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private KujiBoardResponse convertToResponse(KujiBoard board) {
        List<KujiBoardImage> images = kujiBoardImageRepository.findAllByKujiBoardIdOrderBySequenceAsc(board.getId());
        
        return KujiBoardResponse.builder()
                .id(board.getId())
                .title(board.getTitle())
                .pricePerDraw(board.getPricePerDraw())
                .status(board.getStatus())
                .rewardRate(board.getRewardRate())
                .createdAt(board.getCreatedAt())
                .images(images.stream()
                        .map(img -> KujiBoardResponse.KujiBoardImageResponse.builder()
                                .id(img.getId())
                                .imageUrl(img.getImageUrl())
                                .sequence(img.getSequence())
                                .imageType(img.getImageType().name())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}
