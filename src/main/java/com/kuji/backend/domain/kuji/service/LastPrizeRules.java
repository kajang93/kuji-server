// 일반 티켓과 마지막 구매자에게 추가 지급하는 라스트상 규칙을 구분한다.
package com.kuji.backend.domain.kuji.service;

import com.kuji.backend.domain.kuji.entity.KujiItem;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LastPrizeRules {
    private static final Set<String> LAST_GRADES = Set.of("LAST", "LASTONE", "LAST상", "라스트", "라스트상", "라스트원");

    private LastPrizeRules() {}

    public static boolean isLastGrade(String grade) {
        if (grade == null) return false;
        String normalized = grade.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "").replace("_", "");
        return LAST_GRADES.contains(normalized);
    }

    public static String normalizeGrade(String grade) {
        return isLastGrade(grade) ? "LAST" : grade.trim();
    }

    public static long remainingTickets(List<KujiItem> items) {
        return items.stream().filter(item -> !isLastGrade(item.getGrade()))
                .mapToLong(KujiItem::getRemainQty).sum();
    }

    public static void validateLastPrize(List<KujiItem> items) {
        List<KujiItem> lastPrizes = items.stream().filter(item -> isLastGrade(item.getGrade())).toList();
        if (lastPrizes.size() > 1 || lastPrizes.stream().anyMatch(item -> item.getTotalQty() != 1
                || item.getRemainQty() < 0 || item.getRemainQty() > 1)) {
            throw new IllegalArgumentException("라스트상은 한 종류, 수량 1개만 등록할 수 있습니다.");
        }
    }

    public static void validateQuantity(String grade, Integer quantity) {
        if (isLastGrade(grade) && !Integer.valueOf(1).equals(quantity)) {
            throw new IllegalArgumentException("라스트상은 마지막 일반 티켓 구매자에게 추가 지급하므로 수량을 1개로 등록해 주세요.");
        }
    }
}
