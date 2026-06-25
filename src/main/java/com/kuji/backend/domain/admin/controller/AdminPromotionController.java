package com.kuji.backend.domain.admin.controller;

import com.kuji.backend.domain.member.entity.FeePromotion;
import com.kuji.backend.domain.member.repository.FeePromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/promotions")
@RequiredArgsConstructor
public class AdminPromotionController {

    private final FeePromotionRepository feePromotionRepository;

    public record CreatePromotionRequest(
            String title,
            ZonedDateTime startAt,
            ZonedDateTime endAt,
            Integer maxLimit,
            Integer freeMonths
    ) {}

    @GetMapping
    public ResponseEntity<List<FeePromotion>> getAllPromotions() {
        return ResponseEntity.ok(feePromotionRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<FeePromotion> createPromotion(@RequestBody CreatePromotionRequest request) {
        FeePromotion promotion = FeePromotion.builder()
                .title(request.title())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .maxLimit(request.maxLimit())
                .freeMonths(request.freeMonths())
                .build();
        return ResponseEntity.ok(feePromotionRepository.save(promotion));
    }
}
