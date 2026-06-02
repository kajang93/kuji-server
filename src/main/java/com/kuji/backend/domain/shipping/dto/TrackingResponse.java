package com.kuji.backend.domain.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingResponse {
    private String orderNumber;       // 쿠지 당첨내역/주문 번호
    private String trackingNumber;    // 송장번호
    private String courier;           // 택배사 이름
    private String recipientAddress;  // 배송지 주소
    private String deliveryDriver;    // 배송 기사 이름 (Mock)
    private String deliveryDriverPhone; // 기사 연락처 (Mock)
    private List<TrackingDetail> history; // 배송 상세 타임라인

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingDetail {
        private String date;
        private String time;
        private String location;
        private String status;
        private boolean isCompleted;
    }
}
