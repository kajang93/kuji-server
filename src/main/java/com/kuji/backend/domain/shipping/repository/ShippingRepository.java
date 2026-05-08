package com.kuji.backend.domain.shipping.repository;

import com.kuji.backend.domain.shipping.entity.Shipping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShippingRepository extends JpaRepository<Shipping, Long> {
    List<Shipping> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
}
