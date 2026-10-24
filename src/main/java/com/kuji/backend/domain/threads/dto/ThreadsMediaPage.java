// 쓰레드 게시글과 댓글 목록의 페이지 응답을 읽습니다.
package com.kuji.backend.domain.threads.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadsMediaPage(List<Media> data, Paging paging) {
    public String nextCursor() {
        return paging == null || paging.next() == null || paging.next().isBlank() || paging.cursors() == null
                ? null : paging.cursors().after();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Media(String id, String username, String text, String permalink, String timestamp,
                        @JsonProperty("is_reply_owned_by_me") Boolean ownedByMe) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Paging(Cursors cursors, String next) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cursors(String after) {
    }
}
