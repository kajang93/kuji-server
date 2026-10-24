// 메트리쿨의 전날 한국 시간 방문자와 페이지뷰를 조회합니다.
package com.kuji.backend.domain.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kuji.backend.global.infra.ExternalApiHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Component
public class MetricoolWebsiteClient {

    @Value("${metricool.api-token:}")
    private String apiToken;

    @Value("${metricool.user-id:}")
    private String userId;

    @Value("${metricool.blog-id:}")
    private String blogId;

    private final RestTemplate restTemplate = ExternalApiHttpClient.createRestTemplate();

    public Optional<WebsiteStats> fetchDailyStats(LocalDate reportDate) {
        if (!isConfigured(apiToken) || !isConfigured(userId) || !isConfigured(blogId)) {
            log.warn("[운영 리포트] 메트리쿨 API 설정이 없어 방문 통계를 미집계로 표시합니다.");
            return Optional.empty();
        }

        try {
            long visitors = fetchMetric("Visitors", reportDate);
            long pageViews = fetchMetric("PageViews", reportDate);
            return Optional.of(new WebsiteStats(visitors, pageViews));
        } catch (RuntimeException e) {
            // 외부 응답 본문과 인증 헤더는 기록하지 않습니다.
            log.warn("[운영 리포트] 메트리쿨 방문 통계를 조회하지 못했습니다. 오류 유형={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private long fetchMetric(String metric, LocalDate reportDate) {
        String date = reportDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        URI uri = UriComponentsBuilder.fromUriString("https://app.metricool.com/api/stats/timeline/" + metric)
                .queryParam("start", date)
                .queryParam("end", date)
                .queryParam("timezone", "Asia/Seoul")
                .queryParam("userId", userId)
                .queryParam("blogId", blogId)
                .build().encode().toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Mc-Auth", apiToken);
        JsonNode rows = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            throw new IllegalStateException("방문 통계 응답이 비어 있습니다.");
        }

        long total = 0;
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() != 2 || row.get(0).isNull() ||
                    !(row.get(1).isNumber() || row.get(1).isTextual())) {
                throw new IllegalStateException("방문 통계 응답 형식이 올바르지 않습니다.");
            }
            long value = new BigDecimal(row.get(1).asText()).longValueExact();
            if (value < 0) {
                throw new IllegalStateException("방문 통계 값이 올바르지 않습니다.");
            }
            total = Math.addExact(total, value);
        }
        return total;
    }

    private boolean isConfigured(String value) {
        return StringUtils.hasText(value) && !"mock".equalsIgnoreCase(value.trim());
    }

    public record WebsiteStats(long visitors, long pageViews) {
    }
}
