package com.kuji.backend.domain.shipping.repository;

import com.kuji.backend.domain.shipping.entity.Shipping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShippingRepository extends JpaRepository<Shipping, Long> {
    List<Shipping> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    @Query("SELECT DISTINCT s FROM Shipping s JOIN DrawHistory dh ON dh.shipping = s " +
           "WHERE dh.kujiBoard.member.id = :sellerId " +
           "ORDER BY s.createdAt DESC")
    List<Shipping> findAllBySellerId(@Param("sellerId") Long sellerId);
}
