package com.kuji.backend.global.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest httpRequest) {
                MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
                MDC.put("requestUri", httpRequest.getRequestURI());
                
                // If memberId is present in SecurityContext or headers, it can be added here.
                // Since this filter runs before Security filter, memberId might be null.
                // A secondary filter or interceptor could update MDC with memberId after auth.
            }
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
