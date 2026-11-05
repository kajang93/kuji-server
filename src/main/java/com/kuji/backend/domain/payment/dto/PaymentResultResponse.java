// 본인 결제의 저장된 완료 결과와 추첨 화면 복원에 필요한 정보를 제공한다.
package com.kuji.backend.domain.payment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.kuji.backend.domain.payment.enums.SessionType;

public record PaymentResultResponse(SessionType sessionType, Long boardId, Integer count, JsonNode result) {}
