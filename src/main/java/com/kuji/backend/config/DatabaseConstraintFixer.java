package com.kuji.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseConstraintFixer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void fixConstraints() {
        try {
            // Enum 항목(GOOGLE 등)이 추가되었을 때 기존 DB의 체크 제약조건 때문에 발생하는 에러 방지
            jdbcTemplate.execute("ALTER TABLE member DROP CONSTRAINT IF EXISTS member_social_type_check");
            log.info("🔔 기존 member_social_type_check 제약조건을 성공적으로 삭제했습니다.");
            
            // 나중을 대비해 role 제약조건도 삭제 처리
            jdbcTemplate.execute("ALTER TABLE member DROP CONSTRAINT IF EXISTS member_role_check");
        } catch (Exception e) {
            log.warn("🔔 DB 제약조건 삭제 중 문제 발생 (무시 가능): {}", e.getMessage());
        }
    }
}
