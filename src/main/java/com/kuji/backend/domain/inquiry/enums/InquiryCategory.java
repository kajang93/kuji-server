package com.kuji.backend.domain.inquiry.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InquiryCategory {
    SHIPPING("배송"),
    PRODUCT("상품"),
    ACCOUNT("계정/로그인"),
    ETC("기타");

    private final String description;
}
