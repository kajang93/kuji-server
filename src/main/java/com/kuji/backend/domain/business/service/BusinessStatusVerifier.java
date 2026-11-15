// 국세청 사업자등록 상태조회(공공데이터포털)로 계속사업자인지 확인한다. 휴업·폐업·미등록 번호는 판매자 신청을 받지 않는다.
package com.kuji.backend.domain.business.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class BusinessStatusVerifier {

    public enum Status { ACTIVE, SUSPENDED, CLOSED, NOT_FOUND, UNAVAILABLE }

    public record Result(Status status, String message) {
        public boolean canRegister() { return status == Status.ACTIVE; }
    }

    private static final String STATUS_URL = "https://api.odcloud.kr/api/nts-businessman/v1/status";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    // 공공데이터포털 '국세청_사업자등록정보 진위확인 및 상태조회 서비스' 일반 인증키 (Decoding 키)
    @Value("${nts.service-key:}")
    private String serviceKey;

    private final RestTemplate restTemplate = com.kuji.backend.global.infra.ExternalApiHttpClient.createRestTemplate();
    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();
    private record CachedResult(Result result, Instant expiresAt) {}

    public boolean configured() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    @SuppressWarnings("unchecked")
    public Result check(String businessNumber) {
        String number = businessNumber == null ? "" : businessNumber.replace("-", "").trim();
        if (!configured()) {
            return new Result(Status.UNAVAILABLE, "국세청 조회 설정 전이라 번호 형식만 확인했어요. 관리자가 등록증으로 확인합니다.");
        }
        CachedResult cached = cache.get(number);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.result();

        URI uri = UriComponentsBuilder.fromUriString(STATUS_URL).queryParam("serviceKey", serviceKey).encode().build().toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Result result;
        try {
            Map<String, Object> body = restTemplate.postForObject(uri, new HttpEntity<>(Map.of("b_no", List.of(number)), headers), Map.class);
            List<Map<String, Object>> data = body == null ? null : (List<Map<String, Object>>) body.get("data");
            Map<String, Object> row = data == null || data.isEmpty() ? null : data.get(0);
            String code = row == null ? null : String.valueOf(row.getOrDefault("b_stt_cd", ""));
            result = switch (code == null ? "" : code) {
                case "01" -> new Result(Status.ACTIVE, "계속사업자로 확인됐어요. 등록할 수 있는 사업자등록번호입니다.");
                case "02" -> new Result(Status.SUSPENDED, "휴업 중인 사업자등록번호라 신청할 수 없어요.");
                case "03" -> new Result(Status.CLOSED, "폐업한 사업자등록번호라 신청할 수 없어요.");
                default -> new Result(Status.NOT_FOUND, "국세청에 등록되지 않은 사업자등록번호예요. 번호를 다시 확인해 주세요.");
            };
        } catch (RestClientException e) {
            log.warn("[사업자 상태조회] 국세청 조회 실패: {}", e.getClass().getSimpleName());
            return new Result(Status.UNAVAILABLE, "국세청 조회가 잠시 원활하지 않아요. 잠시 후 다시 확인해 주세요.");
        }
        cache.put(number, new CachedResult(result, Instant.now().plus(CACHE_TTL)));
        if (cache.size() > 5000) cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
        return result;
    }

    /** 가입·재신청 시 서버에서 다시 확인한다. 조회 설정이 없으면 기존처럼 관리자 심사에 맡긴다. */
    public void requireRegistrable(String businessNumber) {
        if (!configured()) return;
        Result result = check(businessNumber);
        if (result.status() == Status.UNAVAILABLE) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, result.message());
        }
        if (!result.canRegister()) throw new IllegalArgumentException(result.message());
    }
}
