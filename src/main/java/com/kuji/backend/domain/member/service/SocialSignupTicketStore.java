// 간편 로그인 신규 회원이 약관에 동의하기 전까지 소셜 계정 정보를 짧게 보관한다 (서버 1대 기준 메모리 보관).
package com.kuji.backend.domain.member.service;

import com.kuji.backend.domain.member.enums.SocialType;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SocialSignupTicketStore {

    public record PendingSocialSignup(SocialType socialType, String socialId, String email,
                                      String nickname, String profileImageUrl, Instant expiresAt) {}

    private static final Duration TTL = Duration.ofMinutes(15);
    private final Map<String, PendingSocialSignup> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String issue(SocialType socialType, String socialId, String email, String nickname, String profileImageUrl) {
        purgeExpired();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new PendingSocialSignup(socialType, socialId, email, nickname, profileImageUrl, Instant.now().plus(TTL)));
        return ticket;
    }

    /** 한 번만 사용할 수 있다. 만료되었거나 없으면 비어 있다. */
    public Optional<PendingSocialSignup> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) return Optional.empty();
        PendingSocialSignup pending = tickets.remove(ticket);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) return Optional.empty();
        return Optional.of(pending);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
