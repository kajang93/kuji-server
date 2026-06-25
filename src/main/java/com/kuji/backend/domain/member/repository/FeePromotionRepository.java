package com.kuji.backend.domain.member.repository;

import com.kuji.backend.domain.member.entity.FeePromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface FeePromotionRepository extends JpaRepository<FeePromotion, Long> {

    @Query("SELECT p FROM FeePromotion p WHERE :now BETWEEN p.startAt AND p.endAt AND p.currentCount < p.maxLimit ORDER BY p.createdAt DESC LIMIT 1")
    Optional<FeePromotion> findFirstActivePromotion(@Param("now") ZonedDateTime now);
}
