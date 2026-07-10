// 누적된 쿠지 상품 데이터로 등급별 자동 입력값을 추천하는 서비스
package com.kuji.backend.domain.kuji.service;

import com.kuji.backend.domain.kuji.dto.KujiItemSuggestionResponse;
import com.kuji.backend.domain.kuji.repository.KujiItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KujiItemSuggestionService {

    private static final int MAX_NAME_CANDIDATES = 3;

    private final KujiItemRepository kujiItemRepository;

    public KujiItemSuggestionResponse suggestByGrade(String grade) {
        String normalizedGrade = normalizeGrade(grade);
        List<KujiItemRepository.ItemNameSummary> nameSummaries =
                kujiItemRepository.findTopNamesByGrade(normalizedGrade, PageRequest.of(0, MAX_NAME_CANDIDATES));
        List<Integer> quantities = kujiItemRepository.findTotalQuantitiesByGrade(normalizedGrade);

        long sampleCount = quantities.size();
        String suggestedName = pickName(normalizedGrade, nameSummaries);
        Integer suggestedTotalQty = pickQuantity(normalizedGrade, quantities);
        double confidence = calculateConfidence(sampleCount, nameSummaries);

        return KujiItemSuggestionResponse.builder()
                .grade(normalizedGrade)
                .suggestedName(suggestedName)
                .suggestedTotalQty(suggestedTotalQty)
                .confidence(confidence)
                .sampleCount(sampleCount)
                .nameCandidates(nameSummaries.stream()
                        .map(KujiItemRepository.ItemNameSummary::getName)
                        .toList())
                .build();
    }

    private String normalizeGrade(String grade) {
        if (grade == null || grade.isBlank()) {
            return "A";
        }
        return grade.trim().toUpperCase(Locale.ROOT);
    }

    private String pickName(String grade, List<KujiItemRepository.ItemNameSummary> nameSummaries) {
        if (!nameSummaries.isEmpty()) {
            return nameSummaries.get(0).getName();
        }
        return grade + "상 상품";
    }

    private Integer pickQuantity(String grade, List<Integer> quantities) {
        if (quantities.isEmpty()) {
            return getDefaultQuantityForGrade(grade);
        }

        return quantities.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Comparator
                        .<Map.Entry<Integer, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .orElseGet(() -> getDefaultQuantityForGrade(grade));
    }

    private Integer getDefaultQuantityForGrade(String grade) {
        if (isScarceGrade(grade)) return 1;
        if (grade.equals("C") || grade.equals("D")) return 2;
        if (grade.equals("E") || grade.equals("F")) return 10;
        return 15;
    }

    private boolean isScarceGrade(String grade) {
        return grade.equals("A")
                || grade.equals("B")
                || grade.equals("LAST")
                || grade.equals("LAST賞")
                || grade.equals("라스트")
                || grade.equals("라스트상");
    }

    private double calculateConfidence(long sampleCount, List<KujiItemRepository.ItemNameSummary> nameSummaries) {
        if (sampleCount <= 0 || nameSummaries.isEmpty()) {
            return 0.2;
        }

        long topNameCount = nameSummaries.get(0).getUsageCount();
        double nameAgreement = (double) topNameCount / sampleCount;
        double sampleWeight = Math.min(1.0, sampleCount / 20.0);
        return Math.round((0.3 + (nameAgreement * 0.5) + (sampleWeight * 0.2)) * 100.0) / 100.0;
    }
}
