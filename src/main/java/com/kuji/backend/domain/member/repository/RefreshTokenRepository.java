package com.kuji.backend.domain.member.repository;

import com.kuji.backend.domain.member.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findFirstByMemberIdOrderByCreatedAtDesc(Long memberId);
    void deleteByMemberId(Long memberId);
}
