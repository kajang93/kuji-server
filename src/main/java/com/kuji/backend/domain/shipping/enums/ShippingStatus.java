package com.kuji.backend.domain.shipping.enums;

public enum ShippingStatus {
    COMPLETED,    // 수령 완료
    PREPARING,    // 배송 준비 중
    SHIPPING,     // 배송 중
    DELIVERED,    // 배송 완료
    CANCELLED,    // 배송 취소
    RETURNED      // 반품
}
