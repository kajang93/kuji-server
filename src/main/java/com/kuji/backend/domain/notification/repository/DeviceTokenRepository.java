package com.kuji.backend.domain.notification.repository;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByDeviceIdAndMemberId(String deviceId, Long memberId);
    List<DeviceToken> findAllByMember(Member member);
    void deleteByDeviceIdAndMemberId(String deviceId, Long memberId);
    void deleteByToken(String token); // 유효하지 않은 토큰 정리용
}
