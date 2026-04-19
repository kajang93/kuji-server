package com.kuji.backend.domain.member.service;

import com.kuji.backend.domain.member.dto.LoginRequest;
import com.kuji.backend.domain.member.dto.MemberProfileResponse;
import com.kuji.backend.domain.member.dto.SignUpRequest;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.SocialType;
import com.kuji.backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.kuji.backend.global.jwt.JwtUtil;
import com.kuji.backend.domain.member.enums.RoleType;
import com.kuji.backend.domain.member.entity.BusinessInfo;
import com.kuji.backend.domain.member.repository.BusinessInfoRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final com.kuji.backend.global.infra.kakao.KakaoClient kakaoClient;
    private final BusinessInfoRepository businessInfoRepository;

    /**
     * 내 정보 조회
     */
    public MemberProfileResponse getMyProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다. (ID: " + memberId + ")"));
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
                    String jwtToken = jwtUtil.createToken(member.getId(), member.getEmail());
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

                    Member newMember = Member.builder()
                            .role(com.kuji.backend.domain.member.enums.RoleType.USER)
                            .socialType(SocialType.KAKAO)
                            .socialId(socialId)
                            .email(email)
                            .nickname(nickname)
                            .profileImageUrl(profileImageUrl)
                            .isTermsAgreed(request.getIsTermsAgreed())
                            .isPrivacyAgreed(request.getIsPrivacyAgreed())
                            .isMarketingAgreed(
                                    request.getIsMarketingAgreed() != null ? request.getIsMarketingAgreed() : false)
                            .build();

                    Member savedMember = memberRepository.save(newMember);
                    String jwtToken = jwtUtil.createToken(savedMember.getId(), savedMember.getEmail());

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
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        if (member.getPassword() == null || !passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 검증 통과! 이제 ID 대신 영롱한 JWT 토큰을 발급해서 줍니다!
        return jwtUtil.createToken(member.getId(), member.getEmail());
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
}