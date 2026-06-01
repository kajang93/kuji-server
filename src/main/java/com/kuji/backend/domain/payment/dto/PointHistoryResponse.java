package com.kuji.backend.domain.payment.dto;

import com.kuji.backend.domain.member.entity.PointHistory;
import com.kuji.backend.domain.member.enums.PointType;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
public class PointHistoryResponse {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Long id;
    private final PointType type;
    private final Integer amount;
    private final String description;
    private final String createdAt;

    public PointHistoryResponse(PointHistory history) {
        this.id = history.getId();
        this.type = history.getType();
        this.amount = history.getAmount();
        this.description = history.getDescription();
        this.createdAt = history.getCreatedAt() != null
                ? history.getCreatedAt().format(FORMATTER)
                : null;
    }
}
