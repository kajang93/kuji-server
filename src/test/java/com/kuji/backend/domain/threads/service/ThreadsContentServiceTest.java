package com.kuji.backend.domain.threads.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadsContentServiceTest {

    @Test
    @DisplayName("Gemini 키가 mock일 때 fallback 템플릿 포스트를 정상적으로 생성한다")
    void generateDailyPostContent_fallback() {
        // given
        ThreadsContentService service = new ThreadsContentService();
        ReflectionTestUtils.setField(service, "geminiApiKey", "mock");

        // when
        String postContent = service.generateDailyPostContent();

        // then
        assertThat(postContent).isNotBlank();
        assertThat(postContent.length()).isGreaterThan(10);
    }

    @Test
    @DisplayName("기본 댓글 서식이 공식몰 링크와 카카오톡 문의 링크를 포함한다")
    void generateDefaultReplyContent() {
        // given
        ThreadsContentService service = new ThreadsContentService();

        // when
        String replyContent = service.generateDefaultReplyContent();

        // then
        assertThat(replyContent).contains("kujishop.shop");
        assertThat(replyContent).contains("pf.kakao.com");
    }
}
