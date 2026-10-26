package com.kuji.backend.domain.threads.controller;

import com.kuji.backend.domain.threads.client.ThreadsApiClient;
import com.kuji.backend.domain.threads.dto.ThreadsAccountInfo;
import com.kuji.backend.domain.threads.dto.ThreadsCustomPostRequest;
import com.kuji.backend.domain.threads.dto.ThreadsPostResult;
import com.kuji.backend.domain.threads.service.ThreadsContentService;
import com.kuji.backend.domain.threads.service.ThreadsSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/marketing/threads")
@RequiredArgsConstructor
public class ThreadsMarketingController {

    private final ThreadsApiClient threadsApiClient;
    private final ThreadsContentService threadsContentService;
    private final ThreadsSchedulerService threadsSchedulerService;

    /**
     * 연동된 Threads 계정 및 토큰 상태 확인
     */
    @GetMapping("/me")
    public ResponseEntity<?> getAccountInfo() {
        try {
            ThreadsAccountInfo me = threadsApiClient.getMe();
            return ResponseEntity.ok(Map.of(
                    "status", "CONNECTED",
                    "account", me));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()));
        }
    }

    /**
     * 오늘 발행될 본문 및 첫 댓글 미리보기 (실제 Threads에는 발행하지 않음)
     */
    @GetMapping("/preview")
    public ResponseEntity<Map<String, String>> previewDailyContent() {
        String post = threadsContentService.generateDailyPostContent();
        String reply = threadsContentService.generateDefaultReplyContent();
        return ResponseEntity.ok(Map.of(
                "previewPost", post,
                "previewReply", reply));
    }

    /**
     * 즉시 1회 테스트 포스팅 및 댓글 발행 실행 (AI 생성 기반)
     */
    @PostMapping("/publish-now")
    public ResponseEntity<?> publishNow() {
        try {
            ThreadsPostResult result = threadsSchedulerService.executePostAndReply(null, null);
            return ResponseEntity.ok(result);
        } catch (org.springframework.web.server.ResponseStatusException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /**
     * 관리자가 지정한 커스텀 문구로 즉시 포스팅 및 댓글 발행 실행
     */
    @PostMapping("/custom-post")
    public ResponseEntity<?> publishCustomPost(@RequestBody ThreadsCustomPostRequest request) {
        try {
            ThreadsPostResult result = threadsSchedulerService.executePostAndReply(
                    request.getText(),
                    request.getReplyText());
            return ResponseEntity.ok(result);
        } catch (org.springframework.web.server.ResponseStatusException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }
}
