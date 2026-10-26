package com.kuji.backend.domain.member.repository;

import com.kuji.backend.domain.member.entity.BusinessInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessInfoRepository extends JpaRepository<BusinessInfo, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from BusinessInfo b where b.id = :id")
    java.util.Optional<BusinessInfo> findByIdWithLock(@org.springframework.data.repository.query.Param("id") Long id);
    boolean existsByBusinessNumber(String businessNumber);
}