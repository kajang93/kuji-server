package com.kuji.backend.domain.member.repository;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.SocialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    // 💡 실무 팁: 이메일로 회원을 찾는 기능은 로그인/중복체크 시 필수입니다.
    Optional<Member> findByEmail(String email);

    // 💡 실무 팁: 소셜 로그인 연동 시 가입된 회원인지 확인하기 위해 사용합니다.
    Optional<Member> findBySocialTypeAndSocialId(SocialType socialType, String socialId);
}
