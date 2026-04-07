package com.kuji.backend.global.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

            // 3. 토큰이 진짜면(유효하면) "출입 허가" 도장 쾅!
            if (jwtUtil.validateToken(token)) {
                String email = jwtUtil.getEmail(token);

                // 스프링 시큐리티에게 "이 사람(이메일) 인증된 사람이니까 통과시켜줘!"라고 알려줌
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(email,
                        null, Collections.emptyList());

                // 사용자의 상세 정보(IP 등)를 보관함에 같이 넣어줍니다.
                authentication.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 4. 다음 단계(다른 필터나 컨트롤러)로 넘어가기
        filterChain.doFilter(request, response);
    }
}