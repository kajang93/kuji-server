package com.kuji.backend.domain.member.controller;

import com.kuji.backend.domain.member.dto.LoginRequest;
import com.kuji.backend.domain.member.dto.SignUpRequest;
import com.kuji.backend.domain.member.enums.SocialType;
import com.kuji.backend.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import com.kuji.backend.domain.member.dto.MemberProfileResponse;

@RestController // 💡 "나는 화면(HTML) 말고 데이터(JSON)만 반환하는 전용 웨이터야!" 라는 뜻
@RequestMapping("/api/members") // 💡 식당 주소: "/api/members" 로 들어오는 손님은 내가 다 받겠다!
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService; // 주방장(Service) 호출 준비 완료

    /**
     * 일반(LOCAL) 회원 가입 API
     */
    @PostMapping("/signup")
    public ResponseEntity<Long> signUp(@RequestBody SignUpRequest request) {

        // 1. @RequestBody가 프론트에서 보낸 JSON을 우리가 만든 DTO 상자에 예쁘게 담아줍니다.
        // 2. 주방장(Service)에게 주문서(DTO)를 넘겨서 요리(DB 저장)를 시킵니다.
        Long memberId = memberService.signUp(request, SocialType.LOCAL, null);

        // 3. 요리가 다 되면 생성된 회원번호(ID)를 손님(프론트엔드)에게 전달! 200 OK!
        return ResponseEntity.ok(memberId);
    }

    /**
     * 일반(LOCAL) 회원 로그인 API
     */
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request) {
        // Service에서 검증을 마치고 온 JWT 토큰을 받습니다.
        String token = memberService.login(request);

        // 200 OK와 함께 토큰 반환!
        return ResponseEntity.ok(token);
    }

    /**
     * 내 정보 조회 API (토큰 필수!)
     */
    @GetMapping("/me")
    public ResponseEntity<MemberProfileResponse> getMyProfile(Authentication authentication) {

        // 💡 어제 만든 검문소(Filter)가 토큰을 검사하고, 진짜 주인이면 여기에 이메일을 쏙 넣어줍니다!
        String email = authentication.getName();

        // 주방장(Service)에게 이메일 넘겨주고 정보 받아오기
        MemberProfileResponse profile = memberService.getMyProfile(email);

        return ResponseEntity.ok(profile);
    }

}