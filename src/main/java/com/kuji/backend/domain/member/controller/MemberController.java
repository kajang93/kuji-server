package com.kuji.backend.domain.member.controller;

import com.kuji.backend.domain.member.dto.*;
import com.kuji.backend.domain.member.enums.SocialType;
import com.kuji.backend.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /**
     * 일반(LOCAL) 회원 가입 API
     */
    @PostMapping("/signup")
    public ResponseEntity<Long> signUp(@RequestBody SignUpRequest request) {
        Long memberId = memberService.signUp(request, SocialType.LOCAL, null);
        return ResponseEntity.ok(memberId);
    }

    /**
     * 일반(LOCAL) 회원 로그인 API
     */
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request) {
        String token = memberService.login(request);
        return ResponseEntity.ok(token);
    }

    /**
     * 카카오(KAKAO) 로그인 API
     */
    @PostMapping("/login/kakao")
    public ResponseEntity<LoginResponse> kakaoLogin(@RequestBody KakaoLoginRequest request) {
        LoginResponse response = memberService.loginByKakao(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 내 정보 조회 API (토큰 필수!)
     */
    @GetMapping("/me")
    public ResponseEntity<MemberProfileResponse> getMyProfile(@AuthenticationPrincipal Long memberId) {
        // 💡 필터에서 토큰을 검증하고 심어둔 회원 번호(memberId)를 바로 주입받습니다!
        MemberProfileResponse profile = memberService.getMyProfile(memberId);
        return ResponseEntity.ok(profile);
    }
}