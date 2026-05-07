package com.kuji.backend.domain.kuji.enums;

public enum DrawStatus {
    DRAWN,          // 당첨 완료
    SHIP_REQUESTED, // 배송 요청됨
    SHIPPED,        // 배송 중
    COMPLETED       // 수령 완료
}
