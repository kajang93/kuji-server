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

    /**
     * 내 정보 수정 API (닉네임, 프로필 이미지 변경)
     * Multipart 요청이므로 @RequestPart를 사용합니다.
     */
    @PatchMapping("/me")
    public ResponseEntity<MemberProfileResponse> updateMyProfile(
            @AuthenticationPrincipal Long memberId,
            @RequestPart(value = "request", required = false) UpdateProfileRequest request,
            @RequestPart(value = "profileImage", required = false) org.springframework.web.multipart.MultipartFile profileImage) {
        
        // request가 null로 올 수 있으므로 빈 객체로 초기화 방어
        if (request == null) {
            request = new UpdateProfileRequest(null);
        }
        
        MemberProfileResponse updatedProfile = memberService.updateProfile(memberId, request, profileImage);
        return ResponseEntity.ok(updatedProfile);
    }

    /**
     * 이메일 중복 확인 API
     */
    @GetMapping("/check-email")
    public ResponseEntity<CheckEmailResponse> checkEmail(@RequestParam String email) {
        boolean isAvailable = !memberService.isEmailExist(email);
        return ResponseEntity.ok(new CheckEmailResponse(isAvailable));
    }

    /**
     * 인증문자 발송 API
     */
    @PostMapping("/send-sms")
    public ResponseEntity<Void> sendSms(@RequestBody java.util.Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("휴대폰 번호가 필요합니다.");
        }
        com.kuji.backend.domain.member.service.SmsVerificationService smsService = 
            org.springframework.web.context.support.WebApplicationContextUtils.getWebApplicationContext(
                ((org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest().getServletContext()
            ).getBean(com.kuji.backend.domain.member.service.SmsVerificationService.class);
            
        smsService.sendVerificationCode(phoneNumber);
        return ResponseEntity.ok().build();
    }

    /**
     * 아이디 찾기 API (전화번호와 인증번호로 조회)
     */
    @PostMapping("/find-id")
    public ResponseEntity<FindIdResponse> findId(@RequestBody FindIdRequest request) {
        String fullEmail = memberService.findId(request.phoneNumber(), request.verificationCode());
        return ResponseEntity.ok(new FindIdResponse(fullEmail));
    }

    /**
     * 비밀번호 초기화 API (이메일 + 전화번호 일치 시 임시 비밀번호로 변경)
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        memberService.resetPassword(request.email(), request.phoneNumber());
        return ResponseEntity.ok().build();
    }

    /**
     * 사업자 프로필 조회 API
     */
    @GetMapping("/business-profile")
    public ResponseEntity<BusinessProfileResponse> getBusinessProfile(@AuthenticationPrincipal Long memberId) {
        BusinessProfileResponse response = memberService.getBusinessProfile(memberId);
        return ResponseEntity.ok(response);
    }
}