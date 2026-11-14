// 남은 수량 비례 추첨을 암호학적 난수로 수행하고, 분쟁 대응용 감사 로그를 남긴다.
package com.kuji.backend.domain.kuji.service;

import com.kuji.backend.domain.kuji.entity.KujiItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

@Component
public class KujiDrawPicker {

    // logback-spring.xml 의 com.kuji.backend.audit 로거 → logs/audit.log (JSON, 일 단위 롤링)
    private static final Logger auditLogger = LoggerFactory.getLogger("com.kuji.backend.audit.draw");

    private final RandomGenerator random;

    public KujiDrawPicker() {
        this(new SecureRandom());
    }

    // 테스트에서 결정적 난수를 주입하기 위한 생성자
    KujiDrawPicker(RandomGenerator random) {
        this.random = random;
    }

    /**
     * 일반 티켓을 count 장 추첨하고(남은 수량 비례), 일반 티켓이 모두 소진되면 라스트상을 추가 지급한다.
     * 선택된 상품의 remainQty 를 차감한다. 호출자는 쿠지 판 락을 잡은 상태여야 한다.
     */
    public List<KujiItem> pick(List<KujiItem> allItems, int count, String drawId, Long memberId, Long boardId) {
        List<KujiItem> winners = new ArrayList<>();
        for (int ticket = 1; ticket <= count; ticket++) {
            List<KujiItem> pool = allItems.stream()
                    .filter(item -> !LastPrizeRules.isLastGrade(item.getGrade()) && item.getRemainQty() > 0)
                    .toList();
            long totalRemain = LastPrizeRules.remainingTickets(pool);
            if (totalRemain < 1) {
                throw new IllegalStateException("요청한 수량보다 남은 일반 티켓이 부족합니다.");
            }
            String poolSnapshot = snapshot(pool);
            long roll = random.nextLong(totalRemain) + 1; // 1..totalRemain
            long cumulative = 0;
            for (KujiItem item : pool) {
                cumulative += item.getRemainQty();
                if (roll <= cumulative) {
                    item.decreaseRemainQty();
                    winners.add(item);
                    auditLogger.info("[DRAW_AUDIT] drawId={}, memberId={}, boardId={}, ticket={}/{}, roll={}/{}, pool=[{}], pickedItemId={}, pickedGrade={}",
                            drawId, memberId, boardId, ticket, count, roll, totalRemain, poolSnapshot, item.getId(), item.getGrade());
                    break;
                }
            }
        }
        if (LastPrizeRules.remainingTickets(allItems) == 0) {
            allItems.stream()
                    .filter(item -> LastPrizeRules.isLastGrade(item.getGrade()) && item.getRemainQty() > 0)
                    .findFirst()
                    .ifPresent(item -> {
                        item.decreaseRemainQty();
                        winners.add(item);
                        auditLogger.info("[DRAW_AUDIT] drawId={}, memberId={}, boardId={}, lastPrize=true, pickedItemId={}",
                                drawId, memberId, boardId, item.getId());
                    });
        }
        return winners;
    }

    private static String snapshot(List<KujiItem> pool) {
        return pool.stream()
                .map(item -> item.getId() + ":" + item.getGrade() + ":" + item.getRemainQty())
                .collect(Collectors.joining(","));
    }
}
