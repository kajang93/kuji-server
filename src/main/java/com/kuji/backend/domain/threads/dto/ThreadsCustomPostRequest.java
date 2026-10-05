package com.kuji.backend.domain.threads.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ThreadsCustomPostRequest {
    private String text;
    private String replyText;
}
