package com.kuji.backend.domain.notification.repository;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.notification.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {
    Optional<NotificationSetting> findByMember(Member member);
}
