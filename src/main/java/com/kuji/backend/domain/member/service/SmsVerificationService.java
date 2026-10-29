package com.kuji.backend.domain.member.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuji.backend.domain.member.dto.SignupPhoneVerificationResponse;
import com.kuji.backend.domain.member.entity.SmsLog;
import com.kuji.backend.domain.member.repository.SmsLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsVerificationService {
    @Value("${aligo.api-key:mock}")
    private String aligoApiKey;
    @Value("${aligo.user-id:mock}")
    private String aligoUserId;
    @Value("${aligo.sender:}")
    private String aligoSender;
    @Value("${aligo.mock-enabled:false}")
    private boolean mockEnabled;
    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    private final SmsLogRepository smsLogRepository;
    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private Clock clock = Clock.systemUTC();
    private final Map<String, VerificationData> verificationStore = new ConcurrentHashMap<>();
    private final Map<String, SignupProof> signupProofs = new ConcurrentHashMap<>();
    private final Map<String, Boolean> sending = new ConcurrentHashMap<>();

    private record VerificationData(String code, Instant expiresAt, Instant sentAt, int failedAttempts) {}
    private record SignupProof(String phone, Instant expiresAt, boolean claimed) {}

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    public static String normalizePhoneNumber(String phoneNumber) {
        String normalized = phoneNumber == null ? "" : phoneNumber.replaceAll("[-\\s]", "");
        if (!normalized.matches("01[016789][0-9]{7,8}")) {
            throw new IllegalArgumentException("올바른 휴대폰 번호를 입력해 주세요.");
        }
        return normalized;
    }

    /** 인증번호는 실제 발송이 수락된 후에만 유효하게 저장합니다. */
    public void sendVerificationCode(String phoneNumber) {
        String phone = normalizePhoneNumber(phoneNumber);
        boolean mock = isPlaceholder(aligoApiKey) || isPlaceholder(aligoUserId);
        if (mock && !allowsDevelopmentMock()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "문자 인증 서비스가 준비되지 않았습니다. 고객센터에 문의해 주세요.");
        }
        if (!mock && (aligoSender == null || !aligoSender.matches("[0-9-]{8,20}"))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "문자 인증 서비스가 준비되지 않았습니다. 고객센터에 문의해 주세요.");
        }
        if (sending.putIfAbsent(phone, true) != null) {
            throw new IllegalArgumentException("인증 문자를 발송하고 있습니다. 잠시 후 확인해 주세요.");
        }
        try {
            Instant now = clock.instant();
            verificationStore.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
            signupProofs.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
            VerificationData previous = verificationStore.get(phone);
            if (previous != null && now.isBefore(previous.sentAt().plusSeconds(60))) {
                throw new IllegalArgumentException("인증 문자는 1분 후 다시 요청해 주세요.");
            }
            var startOfDay = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))).atStartOfDay();
            if (smsLogRepository.countByPhoneNumberAndCreatedAtAfter(phone, startOfDay) >= 3) {
                throw new IllegalArgumentException("하루 최대 인증 문자 발송 횟수(3회)를 초과했습니다. 내일 다시 시도해 주세요.");
            }
            String code = String.format("%06d", random.nextInt(1000000));
            if (!mock) sendSms(phone, code);
            smsLogRepository.save(new SmsLog(phone));
            Instant acceptedAt = clock.instant();
            verificationStore.put(phone, new VerificationData(code, acceptedAt.plus(Duration.ofMinutes(3)), acceptedAt, 0));
            log.info("[SMS 발송 수락] mock={}, receiver={}", mock, maskPhone(phone));
        } finally {
            sending.remove(phone);
        }
    }

    private boolean isPlaceholder(String value) {
        return value == null || value.isBlank() || "mock".equalsIgnoreCase(value.trim());
    }

    private boolean allowsDevelopmentMock() {
        var profiles = Arrays.stream((activeProfiles == null ? "" : activeProfiles).split(",")).map(String::trim).toList();
        return mockEnabled && profiles.stream().noneMatch(profile -> profile.equals("prod") || profile.equals("production"))
                && profiles.stream().anyMatch(profile -> profile.equals("local") || profile.equals("dev") || profile.equals("test"));
    }

    private void sendSms(String phone, String code) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("key", aligoApiKey);
            body.add("user_id", aligoUserId);
            body.add("sender", aligoSender);
            body.add("receiver", phone);
            body.add("msg", "[쿠지샵] 인증번호는 [" + code + "] 입니다.");
            ResponseEntity<String> response = restTemplate.postForEntity("https://apis.aligo.in/send/", new HttpEntity<>(body, headers), String.class);
            if (response.getBody() == null || objectMapper.readTree(response.getBody()).path("result_code").asInt(Integer.MIN_VALUE) != 1) {
                throw new IllegalStateException("SMS provider rejected request");
            }
        } catch (Exception e) {
            log.warn("[SMS 발송 실패] receiver={}, errorType={}", maskPhone(phone), e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "인증 문자를 보내지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 비밀번호 재설정 화면의 사전 확인은 실제 변경에서 다시 검증하도록 코드를 유지합니다. */
    public boolean checkVerificationCode(String phoneNumber, String code) {
        validateCode(phoneNumber, code, false);
        return true;
    }

    /** 아이디 찾기·비밀번호 변경처럼 인증을 사용하는 작업에서 한 번만 소비합니다. */
    public boolean verifyCode(String phoneNumber, String code) {
        validateCode(phoneNumber, code, true);
        return true;
    }

    private void validateCode(String phoneNumber, String code, boolean consume) {
        String phone = normalizePhoneNumber(phoneNumber);
        if (code == null || !code.matches("[0-9]{6}")) throw new IllegalArgumentException("인증번호 6자리를 확인해 주세요.");
        AtomicReference<String> failure = new AtomicReference<>();
        Instant now = clock.instant();
        verificationStore.compute(phone, (key, data) -> {
            if (data == null || !now.isBefore(data.expiresAt())) {
                failure.set("인증 시간이 만료되었거나 발송 내역이 없습니다. 다시 인증해 주세요.");
                return null;
            }
            if (!MessageDigest.isEqual(data.code().getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) {
                int attempts = data.failedAttempts() + 1;
                failure.set(attempts >= 5 ? "인증번호 확인 횟수를 초과했습니다. 다시 요청해 주세요." : "인증번호가 일치하지 않습니다.");
                return attempts >= 5 ? null : new VerificationData(data.code(), data.expiresAt(), data.sentAt(), attempts);
            }
            return consume ? null : data;
        });
        if (failure.get() != null) throw new IllegalArgumentException(failure.get());
    }

    public SignupPhoneVerificationResponse verifySignupCode(String phoneNumber, String code) {
        String phone = normalizePhoneNumber(phoneNumber);
        verifyCode(phone, code);
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(10));
        signupProofs.put(tokenHash(token), new SignupProof(phone, expiresAt, false));
        return new SignupPhoneVerificationResponse(token, expiresAt);
    }

    /** 같은 증명으로 가입이 동시에 진행되지 않도록 예약하고 거래 완료 후 한 번만 소비합니다. */
    public void claimSignupVerification(String phoneNumber, String token) {
        String phone = normalizePhoneNumber(phoneNumber);
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("휴대폰 인증을 완료한 뒤 가입해 주세요.");
        }
        String key = tokenHash(token);
        SignupProof reserved = signupProofs.compute(key, (ignored, proof) -> {
            if (proof == null || proof.claimed() || !proof.phone().equals(phone) || !clock.instant().isBefore(proof.expiresAt())) {
                throw new IllegalArgumentException("휴대폰 인증이 만료되었거나 이미 사용되었습니다. 다시 인증해 주세요.");
            }
            return new SignupProof(proof.phone(), proof.expiresAt(), true);
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        signupProofs.remove(key, reserved);
                    } else {
                        signupProofs.computeIfPresent(key, (ignored, current) -> current != reserved ? current
                                : clock.instant().isBefore(current.expiresAt()) ? new SignupProof(current.phone(), current.expiresAt(), false) : null);
                    }
                }
            });
        } else {
            signupProofs.remove(key, reserved);
        }
    }

    private String tokenHash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("인증 정보를 확인하지 못했습니다.");
        }
    }
}
