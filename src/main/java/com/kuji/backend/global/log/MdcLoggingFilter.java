package com.kuji.backend.global.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";
    private static final String MEMBER_ID = "memberId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 1. 요청마다 고유한 Trace ID 생성 (UUID 앞 8자리만 사용해 간결하게 유지)
            String traceId = UUID.randomUUID().toString().substring(0, 8);
            MDC.put(TRACE_ID, traceId);

            // 2. SecurityContext에서 memberId 추출 (로그인 된 경우)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
                try {
                    // CustomUserDetails를 사용하는 경우 내부에서 추출 (여기서는 단순히 Long 타입이라고 가정)
                    String memberId = authentication.getPrincipal().toString();
                    MDC.put(MEMBER_ID, memberId);
                } catch (Exception e) {
                    // 토큰 파싱 에러 등으로 principal을 못 가져오면 패스
                }
            } else {
                MDC.put(MEMBER_ID, "anonymous");
            }

            filterChain.doFilter(request, response);

        } finally {
            // 메모리 누수 방지를 위해 요청이 끝나면 반드시 MDC를 비워줌
            MDC.clear();
        }
    }
}
