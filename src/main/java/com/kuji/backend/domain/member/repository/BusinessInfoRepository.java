package com.kuji.backend.domain.member.repository;

import com.kuji.backend.domain.member.entity.BusinessInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessInfoRepository extends JpaRepository<BusinessInfo, Long> {
}