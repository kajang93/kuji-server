package com.kuji.backend.domain.member.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsVerificationService {

    private static final Logger businessLogger = LoggerFactory.getLogger("BUSINESS_LOGGER");

    @Value("${aligo.api-key:mock}")
    private String aligoApiKey;

    @Value("${aligo.user-id:mock}")
    private String aligoUserId;

    @Value("${aligo.sender:01022210106}")
    private String aligoSender;

    private final RestTemplate restTemplate = new RestTemplate();

    // 휴대폰 번호 -> {인증코드, 생성시간}
    private final Map<String, VerificationData> verificationStore = new ConcurrentHashMap<>();

    private static class VerificationData {
        String code;
        LocalDateTime expirationTime;

        VerificationData(String code, LocalDateTime expirationTime) {
            this.code = code;
            this.expirationTime = expirationTime;
        }
    }

    /**
     * 알리고 SMS API를 통해 인증번호를 발송합니다.
     */
    public void sendVerificationCode(String phoneNumber) {
        // 1. 6자리 난수 생성
        String verificationCode = String.format("%06d", new Random().nextInt(1000000));
        
        // 2. 메모리에 저장 (유효시간 3분)
        verificationStore.put(phoneNumber, new VerificationData(verificationCode, LocalDateTime.now().plusMinutes(3)));
        
        String message = "[쿠지샵] 인증번호는 [" + verificationCode + "] 입니다.";

        // 3. API 키가 mock이면 실제 발송을 생략하고 로그만 찍습니다.
        if ("mock".equals(aligoApiKey)) {
            businessLogger.info("[SMS_SEND_SUCCESS] mock=true, receiver={}, message=\"{}\"", phoneNumber, message);
            return;
        }

        // 4. 알리고 API 호출 (실제 발송)
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("key", aligoApiKey);
            body.add("user_id", aligoUserId);
            body.add("sender", aligoSender);
            body.add("receiver", phoneNumber);
            body.add("msg", message);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity("https://apis.aligo.in/send/", request, String.class);
            String responseBody = response.getBody();

            // 알리고 API는 에러가 나도 HTTP 200 OK를 반환하고 JSON 안에 음수 result_code를 내려줄 때가 있습니다.
            if (responseBody != null && responseBody.contains("\"result_code\":-") || (responseBody != null && responseBody.contains("\"result_code\":\"-"))) {
                businessLogger.error("[SMS_SEND_FAIL] receiver={}, response=\"{}\"", phoneNumber, responseBody);
                throw new RuntimeException("알리고 발송 거절: " + responseBody);
            }

            businessLogger.info("[SMS_SEND_SUCCESS] mock=false, receiver={}, response=\"{}\"", phoneNumber, responseBody);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String aligoError = e.getResponseBodyAsString();
            businessLogger.error("[SMS_SEND_FAIL] receiver={}, HttpError=\"{}\"", phoneNumber, aligoError);
            throw new RuntimeException("알리고 서버 에러: " + aligoError);
        } catch (Exception e) {
            businessLogger.error("[SMS_SEND_FAIL] receiver={}, reason=\"{}\"", phoneNumber, e.getMessage());
            throw new RuntimeException("SMS 전송 시스템 에러: " + e.getMessage());
        }
    }

    /**
     * 사용자가 입력한 인증번호를 검증합니다.
     */
    public boolean verifyCode(String phoneNumber, String code) {
        VerificationData data = verificationStore.get(phoneNumber);
        if (data == null) {
            throw new IllegalArgumentException("인증번호 발송 내역이 없습니다.");
        }

        if (LocalDateTime.now().isAfter(data.expirationTime)) {
            verificationStore.remove(phoneNumber);
            throw new IllegalArgumentException("인증 시간이 만료되었습니다. 다시 시도해주세요.");
        }

        if (!data.code.equals(code)) {
            throw new IllegalArgumentException("인증번호가 일치하지 않습니다.");
        }

        // 검증 성공 시 메모리에서 삭제
        verificationStore.remove(phoneNumber);
        return true;
    }
}
