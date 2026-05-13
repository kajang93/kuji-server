package com.kuji.backend.global.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 요청 헤더에서 "Authorization: Bearer eyJ..." 꺼내기
        String authHeader = request.getHeader("Authorization");

        // 2. 토큰이 있고, "Bearer "로 시작하는지 확인
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            // 3. 토큰이 유효하면 인증 정보 설정
            if (jwtUtil.validateToken(token)) {
                Long memberId = jwtUtil.getMemberId(token);
                String role = jwtUtil.getRole(token);

                // 역할 정보를 시큐리티 권한으로 변환 (ROLE_ 접두사 추가)
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        memberId,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                );

                authentication.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 4. 다음 단계로 넘어가기
        filterChain.doFilter(request, response);
    }
}