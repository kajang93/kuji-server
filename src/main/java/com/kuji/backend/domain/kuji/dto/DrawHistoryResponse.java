package com.kuji.backend.domain.kuji.dto;

import com.kuji.backend.domain.kuji.enums.DrawStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawHistoryResponse {
    private Long id;                // drawHistoryId
    private String boardTitle;      // 쿠지 판 이름 (애니메이션 명 등)
    private String grade;           // 상품 등급 (A, B, C...)
    private String itemName;        // 상품명
    private String itemImageUrl;    // 상품 이미지
    private DrawStatus status;      // 현재 상태 (DRAWN, SHIPPING_REQUESTED 등)
    private LocalDateTime createdAt;// 당첨 일시
    private Long shippingId;        // 배송 ID
    private Integer price;          // 결제 금액 (추가)
}
