package com.kuji.backend.domain.kuji.enums;

public enum DrawStatus {
    DRAWN,              // 당첨 완료
    SHIPPING_REQUESTED, // 배송 요청됨 (DB: SHIPPING_REQUESTED)
    SHIPPING,           // 배송 중 (DB: SHIPPING)
    DELIVERED           // 수령 완료 (DB: DELIVERED)
}
