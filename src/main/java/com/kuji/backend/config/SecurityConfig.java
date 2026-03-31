package com.kuji.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity // 💡 "이제부터 보안 규칙은 내가 관리한다!"
public class SecurityConfig {

    // 💡 BCrypt 암호화 기계를 프로젝트 전역에서 쓸 수 있게 Bean으로 등록합니다.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 💡 식당 입구(URL) 출입 규칙 설정
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API 서버라서 CSRF 방어는 일단 끕니다. (프론트랑 통신할 때 필수)
                .formLogin(form -> form.disable()) // 스프링이 기본 제공하는 못생긴 로그인 화면 안 씁니다.
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        // 회원가입과 로그인은 아무나 들어올 수 있게 허용(permitAll)!
                        .requestMatchers("/api/members/signup", "/api/members/login").permitAll()
                        // 그 외의 주소는 전부 보안 카드(토큰) 검사할 거야!
                        .anyRequest().authenticated());

        return http.build();
    }
}