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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final com.kuji.backend.global.infra.kakao.KakaoClient kakaoClient;
    private final com.kuji.backend.global.infra.naver.NaverClient naverClient;
    private final BusinessInfoRepository businessInfoRepository;
    private final S3Service s3Service;
    private final SmsVerificationService smsVerificationService;

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
                    String jwtToken = jwtUtil.createToken(member.getId(), member.getEmail(), member.getRole().name());
                    return com.kuji.backend.domain.member.dto.LoginResponse.builder()
                            .token(jwtToken)
                            .isNewUser(false)
                            .email(member.getEmail())
                            .nickname(member.getNickname())
                            .profileImageUrl(member.getProfileImageUrl())
                            .build();
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

                    // 닉네임이 제공되지 않았을 경우 (선택 안함) 기본 닉네임 부여
                    String finalNickname = (nickname != null && !nickname.isEmpty()) ? nickname : "카카오유저_" + socialId.substring(0, 6);

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
                    String jwtToken = jwtUtil.createToken(savedMember.getId(), savedMember.getEmail(), savedMember.getRole().name());

                    return com.kuji.backend.domain.member.dto.LoginResponse.builder()
                            .token(jwtToken)
                            .isNewUser(true)
                            .build();
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
                    String jwtToken = jwtUtil.createToken(member.getId(), member.getEmail(), member.getRole().name());
                    return com.kuji.backend.domain.member.dto.LoginResponse.builder()
                            .token(jwtToken)
                            .isNewUser(false)
                            .email(member.getEmail())
                            .nickname(member.getNickname())
                            .profileImageUrl(member.getProfileImageUrl())
                            .build();
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

                    // 닉네임이 제공되지 않았을 경우 (선택 안함) 기본 닉네임 부여
                    String finalNickname = (nickname != null && !nickname.isEmpty()) ? nickname : "네이버유저_" + socialId.substring(0, 6);

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
                    String jwtToken = jwtUtil.createToken(savedMember.getId(), savedMember.getEmail(), savedMember.getRole().name());

                    return com.kuji.backend.domain.member.dto.LoginResponse.builder()
                            .token(jwtToken)
                            .isNewUser(true)
                            .build();
                });
    }

    /**
     * 회원 로그인
     */
    public String login(LoginRequest request) {
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

        // 검증 통과! 이제 ID 대신 영롱한 JWT 토큰을 발급해서 줍니다!
        log.info("Login Success - Email: {}", request.email());
        return jwtUtil.createToken(member.getId(), member.getEmail(), member.getRole().name());
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
}