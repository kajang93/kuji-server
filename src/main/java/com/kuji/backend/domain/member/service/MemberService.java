package com.kuji.backend.domain.member.service;

import com.kuji.backend.domain.member.dto.SignUpRequest;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.SocialType;
import com.kuji.backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

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

        // 3. DTO -> Entity 변환
        Member newMember = request.toEntity(socialType, socialId);

        // 4. DB에 저장
        Member savedMember = memberRepository.save(newMember);

        return savedMember.getId();
    }
}