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

    // [Admin] 특정 시점 이후 가입한 회원 수 집계 (예: 오늘 신규 가입자)
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(m) FROM Member m WHERE m.createdAt >= :startDate")
    Long countMembersCreatedAfter(@org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate);

    Optional<Member> findByPhoneNumber(String phoneNumber);
    
    Optional<Member> findByEmailAndPhoneNumber(String email, String phoneNumber);
}
