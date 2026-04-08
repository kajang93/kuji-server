package com.kuji.backend.global.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component // 💡 "스프링아, 이 기계(객체)를 네가 관리해 줘!"
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationTime;

    // 💡 application.yml에 적어둔 비밀키와 시간을 가져와서 세팅합니다.
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationTime) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationTime = expirationTime;
    }

    /**
     * 로그인 성공 시 프론트엔드에게 줄 토큰을 굽는 메서드
     */
    public String createToken(Long memberId, String email) {
        return Jwts.builder()
                .subject(String.valueOf(memberId)) // 토큰의 주인 (회원 번호로 식별!)
                .claim("email", email) // 이메일은 필요한 경우를 위해 클레임에 따로 넣어줍니다.
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 💡 "이 토큰 가짜 아니야?" 검사하는 메서드
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | io.jsonwebtoken.MalformedJwtException e) {
            System.out.println("❌ 잘못된 JWT 서명입니다.");
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            System.out.println("❌ 만료된 JWT 토큰입니다.");
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            System.out.println("❌ 지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            System.out.println("❌ JWT 토큰이 비어있거나 잘못되었습니다.");
        }
        return false;
    }

    /**
     * 💡 "이 토큰 주인(memberId)이 누구지?" 알아내는 메서드
     */
    public Long getMemberId(String token) {
        String subject = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.valueOf(subject);
    }

    /**
     * 💡 "이 토큰에 적힌 이메일이 뭐지?" 알아내는 메서드 (필요시 사용)
     */
    public String getEmail(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("email", String.class);
    }
}