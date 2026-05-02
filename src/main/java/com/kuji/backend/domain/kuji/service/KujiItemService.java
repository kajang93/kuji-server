package com.kuji.backend.domain.kuji.service;

import com.kuji.backend.domain.kuji.dto.KujiItemCreateRequest;
import com.kuji.backend.domain.kuji.dto.KujiItemResponse;
import com.kuji.backend.domain.kuji.entity.KujiBoard;
import com.kuji.backend.domain.kuji.entity.KujiItem;
import com.kuji.backend.domain.kuji.entity.KujiItemImage;
import com.kuji.backend.domain.kuji.repository.KujiBoardRepository;
import com.kuji.backend.domain.kuji.repository.KujiItemImageRepository;
import com.kuji.backend.domain.kuji.repository.KujiItemRepository;
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
public class KujiItemService {

    private final KujiItemRepository kujiItemRepository;
    private final KujiItemImageRepository kujiItemImageRepository;
    private final KujiBoardRepository kujiBoardRepository;
    private final FileService fileService;

    /**
     * 상품 단건 등록
     */
    @Transactional
    public Long createItem(Long boardId, KujiItemCreateRequest request, List<MultipartFile> images) {
        KujiBoard kujiBoard = kujiBoardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));

        KujiItem kujiItem = KujiItem.builder()
                .grade(request.getGrade())
                .name(request.getName())
                .totalQty(request.getTotalQty())
                .kujiBoard(kujiBoard)
                .build();

        KujiItem savedItem = kujiItemRepository.save(kujiItem);

        if (images != null) {
            for (int i = 0; i < images.size(); i++) {
                String imageUrl = fileService.saveFile("items", images.get(i));
                KujiItemImage itemImage = KujiItemImage.builder()
                        .kujiItem(savedItem)
                        .imageUrl(imageUrl)
                        .sequence(i + 1)
                        .build();
                kujiItemImageRepository.save(itemImage);
            }
        }

        return savedItem.getId();
    }

    /**
     * 상품 대량 등록
     */
    @Transactional
    public void createItems(Long boardId, List<KujiItemCreateRequest> requests, List<MultipartFile> images) {
        KujiBoard kujiBoard = kujiBoardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));

        for (int i = 0; i < requests.size(); i++) {
            KujiItemCreateRequest request = requests.get(i);
            KujiItem kujiItem = KujiItem.builder()
                    .grade(request.getGrade())
                    .name(request.getName())
                    .totalQty(request.getTotalQty())
                    .kujiBoard(kujiBoard)
                    .build();
            KujiItem savedItem = kujiItemRepository.save(kujiItem);
            
            // 대량 등록 시의 이미지 처리
            if (images != null && images.size() > i) {
                MultipartFile image = images.get(i);
                if (image != null && !image.isEmpty() && image.getSize() > 0 && image.getOriginalFilename() != null && !image.getOriginalFilename().equals("empty.bin")) {
                    String imageUrl = fileService.saveFile("items", image);
                    KujiItemImage itemImage = KujiItemImage.builder()
                            .kujiItem(savedItem)
                            .imageUrl(imageUrl)
                            .sequence(1)
                            .build();
                    kujiItemImageRepository.save(itemImage);
                }
            }
        }
    }

    /**
     * 상품 수정
     */
    @Transactional
    public void updateItem(Long itemId, KujiItemCreateRequest request) {
        KujiItem kujiItem = kujiItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        
        String newGrade = request.getGrade() != null ? request.getGrade() : kujiItem.getGrade();
        String newName = request.getName() != null ? request.getName() : kujiItem.getName();
        Integer newQty = request.getTotalQty() != null ? request.getTotalQty() : kujiItem.getTotalQty();

        kujiItem.update(newGrade, newName, newQty);
    }

    /**
     * 상품 삭제
     */
    @Transactional
    public void deleteItem(Long itemId) {
        KujiItem kujiItem = kujiItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        
        kujiItem.validateModifiable();
        kujiItemRepository.delete(kujiItem);
    }

    /**
     * 상품 이미지 수정/추가
     */
    @Transactional
    public void updateItemImage(Long itemId, MultipartFile file) {
        KujiItem kujiItem = kujiItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        
        if (file != null && !file.isEmpty() && file.getSize() > 0) {
            // Delete old images
            kujiItemImageRepository.deleteAll(kujiItemImageRepository.findAllByKujiItemIdOrderBySequenceAsc(itemId));
            
            // Save new image
            String imageUrl = fileService.saveFile("items", file);
            KujiItemImage itemImage = KujiItemImage.builder()
                    .kujiItem(kujiItem)
                    .imageUrl(imageUrl)
                    .sequence(1)
                    .build();
            kujiItemImageRepository.save(itemImage);
        }
    }

    /**
     * 특정 판의 모든 상품 조회
     */
    public List<KujiItemResponse> getItemsByBoardId(Long boardId) {
        return kujiItemRepository.findAllByKujiBoardIdOrderByGradeAsc(boardId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private KujiItemResponse convertToResponse(KujiItem item) {
        List<KujiItemImage> images = kujiItemImageRepository.findAllByKujiItemIdOrderBySequenceAsc(item.getId());
        List<String> imageUrls = images.stream()
                .map(KujiItemImage::getImageUrl)
                .collect(Collectors.toList());

        return KujiItemResponse.builder()
                .id(item.getId())
                .grade(item.getGrade())
                .name(item.getName())
                .totalQty(item.getTotalQty())
                .remainQty(item.getRemainQty())
                .imageUrls(imageUrls)
                .build();
    }
}
