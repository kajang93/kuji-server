package com.kuji.backend.domain.member.controller;

import com.kuji.backend.domain.member.dto.*;
import com.kuji.backend.domain.member.enums.SocialType;
import com.kuji.backend.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final com.kuji.backend.domain.member.service.SmsVerificationService smsVerificationService;

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
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = memberService.login(request);
        ResponseCookie cookie = createRefreshTokenCookie(response.getRefreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    /**
     * 카카오(KAKAO) 로그인 API
     */
    @PostMapping("/login/kakao")
    public ResponseEntity<LoginResponse> kakaoLogin(@RequestBody KakaoLoginRequest request) {
        LoginResponse response = memberService.loginByKakao(request);
        ResponseCookie cookie = createRefreshTokenCookie(response.getRefreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    /**
     * 네이버(NAVER) 로그인 API
     */
    @PostMapping("/login/naver")
    public ResponseEntity<LoginResponse> naverLogin(@RequestBody NaverLoginRequest request) {
        LoginResponse response = memberService.loginByNaver(request);
        ResponseCookie cookie = createRefreshTokenCookie(response.getRefreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    /**
     * 구글(GOOGLE) 로그인 API
     */
    @PostMapping("/login/google")
    public ResponseEntity<LoginResponse> googleLogin(@RequestBody GoogleLoginRequest request) {
        LoginResponse response = memberService.loginByGoogle(request);
        ResponseCookie cookie = createRefreshTokenCookie(response.getRefreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    private ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true) // HTTPS 통신에서만 전송
                .sameSite("None") // CORS 환경에서 쿠키 전송 허용
                .path("/")
                .maxAge(14 * 24 * 60 * 60) // 14일
                .build();
    }

    /**
     * 리프레시 토큰으로 액세스 토큰 재발급 API
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(@CookieValue(value = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        
        LoginResponse response = memberService.refreshAccessToken(refreshToken);
        if (response == null) {
            // 리프레시 토큰이 만료되었거나 유효하지 않음 -> 쿠키 삭제 (로그아웃 처리)
            ResponseCookie deleteCookie = ResponseCookie.from("refresh_token", "")
                    .httpOnly(true).secure(true).sameSite("None").path("/").maxAge(0).build();
            return ResponseEntity.status(401)
                    .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                    .build();
        }
        
        // 새 리프레시 토큰을 쿠키에 설정 (Token Rotation)
        ResponseCookie newRefreshCookie = createRefreshTokenCookie(response.getRefreshToken());
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newRefreshCookie.toString())
                .body(response);
    }

    /**
     * 로그아웃 (쿠키 삭제 및 DB 리프레시 토큰 삭제)
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@AuthenticationPrincipal String memberId) {
        if (memberId != null) {
            memberService.logout(Long.valueOf(memberId));
        }
        ResponseCookie deleteCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true).secure(true).sameSite("None").path("/").maxAge(0).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body("Logged out successfully");
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
    public ResponseEntity<String> sendSms(@RequestBody java.util.Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return ResponseEntity.badRequest().body("전화번호가 필요합니다.");
        }
        smsVerificationService.sendVerificationCode(phoneNumber);
        return ResponseEntity.ok("인증번호가 발송되었습니다.");
    }

    /**
     * 7. 문자 인증번호 검증 API (회원가입 용)
     */
    @PostMapping("/verify-sms")
    public ResponseEntity<String> verifySms(@RequestBody java.util.Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        String code = request.get("code");
        if (phoneNumber == null || code == null) {
            return ResponseEntity.badRequest().body("전화번호와 인증번호가 필요합니다.");
        }
        
        boolean isValid = smsVerificationService.verifyCode(phoneNumber, code);
        if (isValid) {
            return ResponseEntity.ok("인증되었습니다.");
        } else {
            return ResponseEntity.badRequest().body("인증번호가 일치하지 않거나 만료되었습니다.");
        }
    }

    /**
     * 아이디 찾기 API (전화번호와 인증번호로 조회)
     */
    @PostMapping("/find-id")
    public ResponseEntity<FindIdResponse> findId(@RequestBody FindIdRequest request) {
        String fullEmail = memberService.findId(request.phoneNumber(), request.verificationCode(), request.type());
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
     * 로그아웃 상태에서의 즉시 비밀번호 재설정 API
     */
    @PostMapping("/reset-password/direct")
    public ResponseEntity<String> resetPasswordDirect(@RequestBody DirectResetPasswordRequest request) {
        memberService.resetPasswordDirect(request);
        return ResponseEntity.ok("비밀번호가 성공적으로 재설정되었습니다.");
    }

    /**
     * 로그인 유저의 비밀번호 변경 API
     */
    @PatchMapping("/password")
    public ResponseEntity<String> changePassword(@AuthenticationPrincipal Long memberId, @RequestBody ChangePasswordRequest request) {
        memberService.changePassword(memberId, request);
        return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");
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