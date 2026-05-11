package com.kuji.backend.domain.community.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PostCategory {
    FREE("자유게시판"),
    WINNING("당첨인증"),
    QNA("질문답변"),
    NOTICE("공지사항");

    private final String description;
}
