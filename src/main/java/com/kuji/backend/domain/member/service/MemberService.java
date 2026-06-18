package com.kuji.backend.domain.member.service;

import com.kuji.backend.domain.member.dto.LoginRequest;
import com.kuji.backend.domain.member.dto.MemberProfileResponse;
import com.kuji.backend.domain.member.dto.SignUpRequest;
import com.kuji.backend.domain.member.dto.UpdateProfileRequest;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.SocialType;
import com.kuji.backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import com.kuji.backend.global.jwt.JwtUtil;
import com.kuji.backend.domain.member.enums.RoleType;
import com.kuji.backend.domain.member.entity.BusinessInfo;
import com.kuji.backend.domain.member.repository.BusinessInfoRepository;
import com.kuji.backend.global.service.S3Service;
import com.kuji.backend.global.infra.kakao.KakaoClient;
import com.kuji.backend.global.infra.naver.NaverClient;
import com.kuji.backend.global.infra.google.GoogleClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final KakaoClient kakaoClient;
    private final NaverClient naverClient;
    private final GoogleClient googleClient;
    private final BusinessInfoRepository businessInfoRepository;
    private final S3Service s3Service;
    private final SmsVerificationService smsVerificationService;
    private final com.kuji.backend.domain.member.repository.RefreshTokenRepository refreshTokenRepository;

    private com.kuji.backend.domain.member.dto.LoginResponse generateLoginResponse(Member member, boolean isNewUser) {
        String accessToken = jwtUtil.createToken(member.getId(), member.getEmail(), member.getRole().name());
        String refreshTokenStr = jwtUtil.createRefreshToken(member.getId());

        // 기존 리프레시 토큰이 있으면 먼저 삭제 (PK 수정 불가로 인한 예외 방지)
        refreshTokenRepository.findByMemberId(member.getId())
                .ifPresent(token -> {
                    refreshTokenRepository.delete(token);
                    refreshTokenRepository.flush();
                });
        
        java.time.LocalDateTime expiresAt = java.time.LocalDateTime.now().plusDays(14);
        com.kuji.backend.domain.member.entity.RefreshToken newRefreshToken = com.kuji.backend.domain.member.entity.RefreshToken.builder()
                .tokenValue(refreshTokenStr)
                .memberId(member.getId())
                .expiresAt(expiresAt)
                .build();
                
        refreshTokenRepository.save(newRefreshToken);

        return com.kuji.backend.domain.member.dto.LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshTokenStr)
                .isNewUser(isNewUser)
                .email(member.getEmail())
                .nickname(member.getNickname())
                .profileImageUrl(member.getProfileImageUrl())
                .build();
    }

    /**
     * 내 정보 조회
     */
    public MemberProfileResponse getMyProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다. (ID: " + memberId + ")"));
        return MemberProfileResponse.from(member);
    }

    /**
     * 내 프로필 수정 (닉네임 + 프로필 이미지)
     */
    @Transactional
    public MemberProfileResponse updateProfile(Long memberId, UpdateProfileRequest request, MultipartFile profileImage) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다."));

        // 이미지 파일이 있으면 S3에 저장 후 URL 반환
        String imageUrl = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            // 기존 프로필 이미지가 S3에 있다면 쓰레기 데이터 청소 (삭제)
            if (member.getProfileImageUrl() != null && member.getProfileImageUrl().startsWith("http")) {
                s3Service.deleteFile(member.getProfileImageUrl());
            }
            imageUrl = s3Service.uploadFile("profiles", profileImage);
        }

        member.updateProfile(request.nickname(), imageUrl);
        return MemberProfileResponse.from(member);
    }

    /**
     * 회원 가입 (일반 이메일 및 소셜 통합)
     */
    @Transactional
    public Long signUp(SignUpRequest request, SocialType socialType, String socialId) {

        // 1. 이메일 중복 체크
        if (memberRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        // 2. 소셜 로그인 중복 체크
        if (socialType != SocialType.LOCAL && socialId != null) {
            if (memberRepository.findBySocialTypeAndSocialId(socialType, socialId).isPresent()) {
                throw new IllegalArgumentException("이미 연동된 소셜 계정입니다.");
            }
        }

        // 💡 비밀번호가 있으면 암호화, 소셜 가입이라 없으면 null 처리
        String encodedPassword = (request.password() != null)
                ? passwordEncoder.encode(request.password())
                : null;

        // 💡 암호화된 비밀번호를 넘겨줍니다.
        Member newMember = java.util.Objects.requireNonNull(request.toEntity(socialType, socialId, encodedPassword));
        Member savedMember = memberRepository.save(newMember);

        // 💡 만약 권한이 BIZ(사업자)라면 사업자 정보도 함께 생성하여 저장합니다!
        if (request.role() == RoleType.BIZ) {
            BusinessInfo businessInfo = BusinessInfo.builder()
                    .member(savedMember)
                    .businessNumber(request.businessNumber())
                    .companyName(request.companyName())
                    .ceoName(request.ceoName())
                    .licenseImageUrl("pending_upload") // 💡 추후 파일 업로드 로직과 연결 필요
                    .build();
            businessInfoRepository.save(businessInfo);
        }

        return java.util.Objects.requireNonNull(savedMember).getId();
    }

    // 💡 랜덤 닉네임 생성기 (최신 트렌드 반영)
    private String generateRandomNickname() {
        String[] ADJECTIVES = {
                "행운이 가득한", "대박을 기원하는", "신의 손을 가진", "금손을 인증한", 
                "쿠지를 사랑하는", "매일매일 신나는", "가챠에 진심인", "행복을 부르는",
                "A상을 노리는", "원패스를 꿈꾸는", "두근두근 설레는", "열정 넘치는"
        };
        String[] NOUNS = {
                "다람쥐", "고양이", "너구리", "강아지", "오리", 
                "알파카", "쿼카", "토끼", "올빼미", "쿠지러", "행운요정"
        };
        
        java.util.Random random = new java.util.Random();
        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[random.nextInt(NOUNS.length)];
        
        return adjective + " " + noun;
    }

    /**
     * 카카오 로그인 (신규 가입 시 약관 동의 절차 포함)
     */
    @Transactional
    public com.kuji.backend.domain.member.dto.LoginResponse loginByKakao(
            com.kuji.backend.domain.member.dto.KakaoLoginRequest request) {
        // 1. 카카오 서버에서 사용자 정보 가져오기
        var userInfo = kakaoClient.getKakaoUserInfo(request.getKakaoAccessToken());
        String socialId = String.valueOf(userInfo.getId());
        String email = userInfo.getEmail();
        String nickname = userInfo.getNickname();
        String profileImageUrl = userInfo.getProfileImageUrl();

        System.out.println("🔔 [Kakao-Login] 카카오 사용자 정보 수신 - ID: " + socialId + ", 이메일: " + email);

        // 2. 기존 가입자인지 확인
        return memberRepository.findBySocialTypeAndSocialId(SocialType.KAKAO, socialId)
                .map(member -> {
                    System.out.println("🔔 [Kakao-Login] 기존 회원 로그인 - ID: " + member.getId());
                    return generateLoginResponse(member, false);
                })
                .orElseGet(() -> {
                    // 3. 신규 회원일 경우 -> 약관 동의 체크 여부 확인 (방법 B 전략)
                    if (request.getIsTermsAgreed() == null || !request.getIsTermsAgreed()) {
                        System.out.println("🔔 [Kakao-Login] 신규 회원 - 약관 동의 필요");
                        return com.kuji.backend.domain.member.dto.LoginResponse.builder()
                                .token(null)
                                .isNewUser(true)
                                .email(email)
                                .nickname(nickname)
                                .profileImageUrl(profileImageUrl)
                                .build();
                    }

                    // 4. 약관 동의가 확인되면 회원 가입 진행
                    System.out.println("🔔 [Kakao-Login] 신규 회원 - 회원가입 완료");

                    if (email != null && !email.isEmpty() && memberRepository.findByEmail(email).isPresent()) {
                        throw new IllegalArgumentException("이미 해당 이메일로 가입된 계정이 존재합니다.");
                    }

                    // 닉네임이 제공되지 않았을 경우 (선택 안함) 귀여운 랜덤 닉네임 부여
                    String finalNickname = (nickname != null && !nickname.isEmpty()) ? nickname : generateRandomNickname();

                    Member newMember = Member.builder()
                            .role(com.kuji.backend.domain.member.enums.RoleType.USER)
                            .socialType(SocialType.KAKAO)
                            .socialId(socialId)
                            .email(email)
                            .nickname(finalNickname)
                            .profileImageUrl(profileImageUrl)
                            .isTermsAgreed(request.getIsTermsAgreed())
                            .isPrivacyAgreed(request.getIsPrivacyAgreed())
                            .isMarketingAgreed(
                                    request.getIsMarketingAgreed() != null ? request.getIsMarketingAgreed() : false)
                            .build();

                    Member savedMember = memberRepository.save(newMember);
                    return generateLoginResponse(savedMember, true);
                });
    }

    /**
     * 네이버 로그인 (신규 가입 시 약관 동의 절차 포함)
     */
    @Transactional
    public com.kuji.backend.domain.member.dto.LoginResponse loginByNaver(
            com.kuji.backend.domain.member.dto.NaverLoginRequest request) {
        // 1. 네이버 서버에서 사용자 정보 가져오기
        var userInfo = naverClient.getNaverUserInfo(request.getNaverAccessToken());
        String socialId = userInfo.getId();
        String email = userInfo.getEmail();
        String nickname = userInfo.getNickname();
        String profileImageUrl = userInfo.getProfileImageUrl();

        System.out.println("🔔 [Naver-Login] 네이버 사용자 정보 수신 - ID: " + socialId + ", 이메일: " + email);

        // 2. 기존 가입자인지 확인
        return memberRepository.findBySocialTypeAndSocialId(SocialType.NAVER, socialId)
                .map(member -> {
                    System.out.println("🔔 [Naver-Login] 기존 회원 로그인 - ID: " + member.getId());
                    return generateLoginResponse(member, false);
                })
                .orElseGet(() -> {
                    // 3. 신규 회원일 경우 -> 약관 동의 체크 여부 확인
                    if (request.getIsTermsAgreed() == null || !request.getIsTermsAgreed()) {
                        System.out.println("🔔 [Naver-Login] 신규 회원 - 약관 동의 필요");
                        return com.kuji.backend.domain.member.dto.LoginResponse.builder()
                                .token(null)
                                .isNewUser(true)
                                .email(email)
                                .nickname(nickname)
                                .profileImageUrl(profileImageUrl)
                                .build();
                    }

                    // 4. 약관 동의가 확인되면 회원 가입 진행
                    System.out.println("🔔 [Naver-Login] 신규 회원 - 회원가입 완료");

                    if (email != null && !email.isEmpty() && memberRepository.findByEmail(email).isPresent()) {
                        throw new IllegalArgumentException("이미 해당 이메일로 가입된 계정이 존재합니다.");
                    }

                    // 닉네임이 제공되지 않았을 경우 (선택 안함) 귀여운 랜덤 닉네임 부여
                    String finalNickname = (nickname != null && !nickname.isEmpty()) ? nickname : generateRandomNickname();

                    Member newMember = Member.builder()
                            .role(com.kuji.backend.domain.member.enums.RoleType.USER)
                            .socialType(SocialType.NAVER)
                            .socialId(socialId)
                            .email(email)
                            .nickname(finalNickname)
                            .profileImageUrl(profileImageUrl)
                            .isTermsAgreed(request.getIsTermsAgreed())
                            .isPrivacyAgreed(request.getIsPrivacyAgreed())
                            .isMarketingAgreed(
                                    request.getIsMarketingAgreed() != null ? request.getIsMarketingAgreed() : false)
                            .build();

                    Member savedMember = memberRepository.save(newMember);
                    return generateLoginResponse(savedMember, true);
                });
    }

    /**
     * 구글 로그인 (신규 가입 시 약관 동의 절차 포함)
     */
    @Transactional
    public com.kuji.backend.domain.member.dto.LoginResponse loginByGoogle(
            com.kuji.backend.domain.member.dto.GoogleLoginRequest request) {
        // 1. 구글 서버에서 사용자 정보 가져오기
        var userInfo = googleClient.getGoogleUserInfo(request.getGoogleAccessToken());
        String socialId = userInfo.getId();
        String email = userInfo.getEmail();
        String nickname = userInfo.getNickname();
        String profileImageUrl = userInfo.getProfileImageUrl();

        System.out.println("🔔 [Google-Login] 구글 사용자 정보 수신 - ID: " + socialId + ", 이메일: " + email);

        // 2. 기존 가입자인지 확인
        return memberRepository.findBySocialTypeAndSocialId(SocialType.GOOGLE, socialId)
                .map(member -> {
                    System.out.println("🔔 [Google-Login] 기존 회원 로그인 - ID: " + member.getId());
                    return generateLoginResponse(member, false);
                })
                .orElseGet(() -> {
                    // 3. 신규 회원일 경우 -> 약관 동의 체크 여부 확인
                    if (request.getIsTermsAgreed() == null || !request.getIsTermsAgreed()) {
                        System.out.println("🔔 [Google-Login] 신규 회원 - 약관 동의 필요");
                        return com.kuji.backend.domain.member.dto.LoginResponse.builder()
                                .token(null)
                                .isNewUser(true)
                                .email(email)
                                .nickname(nickname)
                                .profileImageUrl(profileImageUrl)
                                .build();
                    }

                    // 4. 약관 동의가 확인되면 회원 가입 진행
                    System.out.println("🔔 [Google-Login] 신규 회원 - 회원가입 완료");

                    if (email != null && !email.isEmpty() && memberRepository.findByEmail(email).isPresent()) {
                        throw new IllegalArgumentException("이미 해당 이메일로 가입된 계정이 존재합니다.");
                    }

                    // 구글은 실명이 넘어오는 경우가 많으므로 개인정보 보호를 위해 무조건 귀여운 랜덤 닉네임 부여
                    String finalNickname = generateRandomNickname();

                    Member newMember = Member.builder()
                            .role(com.kuji.backend.domain.member.enums.RoleType.USER)
                            .socialType(SocialType.GOOGLE)
                            .socialId(socialId)
                            .email(email)
                            .nickname(finalNickname)
                            .profileImageUrl(profileImageUrl)
                            .isTermsAgreed(request.getIsTermsAgreed())
                            .isPrivacyAgreed(request.getIsPrivacyAgreed())
                            .isMarketingAgreed(
                                    request.getIsMarketingAgreed() != null ? request.getIsMarketingAgreed() : false)
                            .build();

                    Member savedMember = memberRepository.save(newMember);
                    return generateLoginResponse(savedMember, true);
                });
    }

    /**
     * 회원 로그인
     */
    public com.kuji.backend.domain.member.dto.LoginResponse login(LoginRequest request) {
        int pwdLength = (request.password() != null) ? request.password().length() : 0;
        
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Login Failed (User Not Found) - Email: {}, PasswordLength: {}", request.email(), pwdLength);
                    return new IllegalArgumentException("가입되지 않은 이메일입니다.");
                });

        if (member.getPassword() == null || !passwordEncoder.matches(request.password(), member.getPassword())) {
            log.warn("Login Failed (Invalid Password) - Email: {}, PasswordLength: {}", request.email(), pwdLength);
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        log.info("Login Success - Email: {}", request.email());
        return generateLoginResponse(member, false);
    }

    /**
     * 내 프로필 정보 조회
     */
    public MemberProfileResponse getMyProfile(String email) {
        // 1. 이메일로 DB에서 회원 찾기
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));

        // 2. 찾은 Entity를 예쁜 DTO 상자에 담아서 반환
        return MemberProfileResponse.from(member);
    }

    /**
     * 아이디 찾기 (전화번호와 인증번호로 검증 후 전체 이메일 반환)
     */
    public String findId(String phoneNumber, String verificationCode) {
        // 1. 인증번호 검증 (실패 시 예외 발생)
        smsVerificationService.verifyCode(phoneNumber, verificationCode);

        // 2. 전화번호로 회원 조회
        Member member = memberRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new IllegalArgumentException("해당 전화번호로 가입된 회원이 없습니다."));
        
        String email = member.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일로 가입된 계정이 아닙니다 (소셜 가입 등).");
        }
        
        // 인증에 성공했으므로 마스킹 없이 전체 이메일을 반환
        return email;
    }

    /**
     * 비밀번호 초기화 (이메일, 전화번호 검증 후 임시 비밀번호로 변경)
     */
    @Transactional
    public void resetPassword(String email, String phoneNumber) {
        Member member = memberRepository.findByEmailAndPhoneNumber(email, phoneNumber)
                .orElseThrow(() -> new IllegalArgumentException("일치하는 회원 정보가 없습니다."));
        
        // 임시 비밀번호 설정 (데모용)
        String tempPassword = passwordEncoder.encode("temp1234!");
        
        member.updatePassword(tempPassword);
    }

    /**
     * 이메일 중복 여부 확인
     */
    public boolean isEmailExist(String email) {
        return memberRepository.findByEmail(email).isPresent();
    }

    /**
     * 사업자 프로필 조회
     */
    public com.kuji.backend.domain.member.dto.BusinessProfileResponse getBusinessProfile(Long memberId) {
        com.kuji.backend.domain.member.entity.BusinessInfo businessInfo = businessInfoRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("사업자 정보를 찾을 수 없습니다."));
        return com.kuji.backend.domain.member.dto.BusinessProfileResponse.from(businessInfo);
    }

    /**
     * 리프레시 토큰 검증 및 새 액세스 토큰 발급
     */
    @Transactional
    public String refreshAccessToken(String refreshTokenStr) {
        // 1. 토큰 유효성 검증
        if (!jwtUtil.validateToken(refreshTokenStr)) {
            return null; // 만료되거나 손상된 토큰
        }

        // 2. 토큰에서 memberId 추출
        Long memberId = jwtUtil.getMemberId(refreshTokenStr);

        // 3. DB에 저장된 리프레시 토큰과 일치하는지, 만료되지 않았는지 확인
        com.kuji.backend.domain.member.entity.RefreshToken storedToken = refreshTokenRepository.findByMemberId(memberId)
                .orElse(null);

        if (storedToken == null || !storedToken.getTokenValue().equals(refreshTokenStr)) {
            return null; // 토큰 탈취 가능성 처리 (DB와 불일치)
        }
        
        if (storedToken.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            refreshTokenRepository.deleteByMemberId(memberId);
            return null; // DB 상 만료됨
        }

        // 4. 회원 확인 및 새 액세스 토큰 발급
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            return null;
        }

        return jwtUtil.createToken(member.getId(), member.getEmail(), member.getRole().name());
    }

    /**
     * 로그아웃 시 리프레시 토큰 삭제
     */
    @Transactional
    public void logout(Long memberId) {
        refreshTokenRepository.deleteByMemberId(memberId);
    }
}