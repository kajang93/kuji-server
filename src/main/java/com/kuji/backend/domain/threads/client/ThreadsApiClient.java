package com.kuji.backend.domain.threads.client;

import com.kuji.backend.domain.threads.dto.ThreadsAccountInfo;
import com.kuji.backend.domain.threads.dto.ThreadsMediaPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import com.kuji.backend.global.infra.ExternalApiHttpClient;

import java.util.Map;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class ThreadsApiClient {

    private static final String BASE_URL = "https://graph.threads.net/v1.0";

    @Value("${threads.access-token}")
    private String accessToken;

    @Value("${threads.user-id:me}")
    private String configuredUserId;

    private final RestTemplate restTemplate = ExternalApiHttpClient.createRestTemplate();

    /**
     * 현재 연동된 Threads 계정 정보 조회 (id, username, name)
     */
    public ThreadsAccountInfo getMe() {
        String url = BASE_URL + "/me?fields=id,username,name";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null) {
                return ThreadsAccountInfo.builder()
                        .id(String.valueOf(body.get("id")))
                        .username((String) body.get("username"))
                        .name((String) body.get("name"))
                        .build();
            }
        } catch (Exception e) {
            log.error("[Threads API] 계정 정보 조회 실패 ({})", e.getClass().getSimpleName());
            throw new IllegalStateException("Threads 계정 정보를 확인하지 못했습니다. 토큰과 연결 상태를 확인해주세요.");
        }
        return null;
    }

    public ThreadsMediaPage getRecentPostPage(Instant since, String after) {
        return getMediaPage("/" + getTargetUserId() + "/threads", "id,text,username,timestamp", after,
                since.atZone(ZoneOffset.UTC).toLocalDate().toString());
    }

    public ThreadsMediaPage getConversationPage(String postId, String after) {
        if (postId == null || !postId.matches("[0-9]{1,64}")) {
            throw new IllegalArgumentException("Threads 게시글 식별자가 올바르지 않습니다.");
        }
        return getMediaPage("/" + postId + "/conversation",
                "id,text,username,permalink,timestamp,is_reply_owned_by_me", after, null);
    }

    private ThreadsMediaPage getMediaPage(String path, String fields, String after, String since) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(BASE_URL + path)
                .queryParam("fields", fields).queryParam("limit", 100);
        if (after != null && !after.isBlank()) {
            builder.queryParam("after", after);
        }
        if (since != null) {
            builder.queryParam("since", since);
        } else {
            builder.queryParam("reverse", true);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        try {
            ThreadsMediaPage body = restTemplate.exchange(builder.build().encode().toUri(), HttpMethod.GET,
                    new HttpEntity<>(headers), ThreadsMediaPage.class).getBody();
            if (body == null) {
                throw new IllegalStateException("Threads 댓글 조회 응답이 비어 있습니다.");
            }
            return body;
        } catch (RestClientException e) {
            log.warn("[Threads 댓글 API] 조회 실패. threads_read_replies 권한과 토큰을 확인하세요. ({})", e.getClass().getSimpleName());
            throw new IllegalStateException("Threads 게시글 또는 댓글을 조회하지 못했습니다. threads_read_replies 권한과 토큰을 확인해주세요.");
        }
    }

    /**
     * 타겟 Threads 사용자 ID 획득 (설정값 우선, 없으면 me)
     */
    public String getTargetUserId() {
        if (configuredUserId != null && !configuredUserId.isBlank() && !"mock".equalsIgnoreCase(configuredUserId)) {
            return configuredUserId;
        }
        return "me";
    }

    /**
     * 본문 텍스트 포스트 컨테이너 생성
     * 
     * @param text 게시글 내용
     * @return creation_id (미디어 컨테이너 ID)
     */
    public String createTextPostContainer(String text) {
        String userId = getTargetUserId();
        String url = String.format("%s/%s/threads", BASE_URL, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("media_type", "TEXT");
        params.add("text", text);
        params.add("access_token", accessToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            log.info("[Threads API] 포스트 컨테이너 생성 요청 (userId: {})", userId);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("id")) {
                String creationId = String.valueOf(body.get("id"));
                log.info("[Threads API] 포스트 컨테이너 생성 성공 (creationId: {})", creationId);
                return creationId;
            }
            throw new RuntimeException("Threads 컨테이너 생성 응답에 id가 없습니다: " + body);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("[Threads API] 컨테이너 생성 실패: {}", e.getStatusCode());
            throw new IllegalStateException("Threads 컨테이너 생성 요청이 거절되었습니다. HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            log.error("[Threads API] 컨테이너 생성 예외 ({})", e.getClass().getSimpleName());
            throw new IllegalStateException("Threads 컨테이너 생성 요청을 처리하지 못했습니다.");
        }
    }

    /**
     * 특정 포스트에 달릴 댓글(Reply) 컨테이너 생성
     * 
     * @param replyToPostId 답글을 달 원본 게시글 ID
     * @param replyText     댓글 내용
     * @return creation_id (댓글 미디어 컨테이너 ID)
     */
    public String createReplyContainer(String replyToPostId, String replyText) {
        String userId = getTargetUserId();
        String url = String.format("%s/%s/threads", BASE_URL, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("media_type", "TEXT");
        params.add("text", replyText);
        params.add("reply_to_id", replyToPostId);
        params.add("access_token", accessToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            log.info("[Threads API] 댓글 컨테이너 생성 요청 (replyTo: {})", replyToPostId);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("id")) {
                String replyCreationId = String.valueOf(body.get("id"));
                log.info("[Threads API] 댓글 컨테이너 생성 성공 (replyCreationId: {})", replyCreationId);
                return replyCreationId;
            }
            throw new RuntimeException("Threads 댓글 컨테이너 생성 응답에 id가 없습니다: " + body);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("[Threads API] 댓글 컨테이너 생성 실패: {}", e.getStatusCode());
            throw new IllegalStateException("Threads 댓글 컨테이너 생성 요청이 거절되었습니다. HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            log.error("[Threads API] 댓글 컨테이너 생성 예외 ({})", e.getClass().getSimpleName());
            throw new IllegalStateException("Threads 댓글 컨테이너 생성 요청을 처리하지 못했습니다.");
        }
    }

    /**
     * 생성된 컨테이너를 실제로 피드에 게시 (Publish)
     * 
     * @param creationId 미디어 컨테이너 ID
     * @return 게시된 Threads 포스트 ID
     */
    public String publishContainer(String creationId) {
        String userId = getTargetUserId();
        String url = String.format("%s/%s/threads_publish", BASE_URL, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("creation_id", creationId);
        params.add("access_token", accessToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            log.info("[Threads API] 컨테이너 게시(Publish) 요청 (creationId: {})", creationId);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("id")) {
                String publishedId = String.valueOf(body.get("id"));
                log.info("[Threads API] ✅ Threads 게시 완료 (publishedId: {})", publishedId);
                return publishedId;
            }
            throw new RuntimeException("Threads 게시 응답에 id가 없습니다: " + body);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("[Threads API] 게시 실패: {}", e.getStatusCode());
            throw new IllegalStateException("Threads 게시 요청이 거절되었습니다. HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            log.error("[Threads API] 게시 예외 ({})", e.getClass().getSimpleName());
            throw new IllegalStateException("Threads 게시 요청을 처리하지 못했습니다.");
        }
    }
}
