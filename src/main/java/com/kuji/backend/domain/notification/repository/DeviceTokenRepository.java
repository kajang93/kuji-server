package com.kuji.backend.domain.notification.repository;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByDeviceIdAndMemberId(String deviceId, Long memberId);

    // 같은 기기·토큰의 계정 변경을 여러 서버에서도 순서대로 처리합니다.
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended(:identity, 0))", nativeQuery = true)
    Integer lockIdentity(@Param("identity") String identity);

    @Modifying(flushAutomatically = true)
    @Query("delete from DeviceToken token where (token.deviceId = :deviceId or token.token = :token) "
            + "and (token.member.id <> :memberId or token.deviceId <> :deviceId)")
    int deleteOtherBindings(@Param("deviceId") String deviceId, @Param("token") String token,
                            @Param("memberId") Long memberId);
    List<DeviceToken> findAllByMember(Member member);
    void deleteByDeviceIdAndMemberId(String deviceId, Long memberId);
    void deleteByToken(String token); // 유효하지 않은 토큰 정리용
}
