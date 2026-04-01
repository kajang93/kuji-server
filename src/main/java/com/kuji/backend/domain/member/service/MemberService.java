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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 회원 가입 (일반 이메일 및 소셜 통합)
     */
    @Transactional
    public Long signUp(SignUpRequest request, SocialType socialType, String socialId) {

        // 1. 이메일 중복 체크 (개발자님이 만든 findByEmail 활용!)
        // isPresent()를 쓰면 값이 존재할 때(즉, 이미 가입된 이메일일 때) true를 반환합니다.
        if (memberRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        // 2. 소셜 로그인 중복 체크 (선택 사항이지만 안전을 위해 추가)
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
        Member newMember = request.toEntity(socialType, socialId, encodedPassword);
        return memberRepository.save(newMember).getId();
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