package com.kuji.backend.domain.kuji.repository;

import com.kuji.backend.domain.kuji.entity.DrawHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import com.kuji.backend.domain.shipping.entity.Shipping;

@Repository
public interface DrawHistoryRepository extends JpaRepository<DrawHistory, Long> {
    List<DrawHistory> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<DrawHistory> findAllByShipping(Shipping shipping); // 배송 꾸러미별 상품 조회용 추가
    List<DrawHistory> findTop20ByOrderByCreatedAtDesc(); // 티커용 최신 20건 조회

    // [Seller] 사업자의 쿠지판에서 발생한 배송 대기 등 특정 상태의 내역 건수 조회
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(d) FROM DrawHistory d WHERE d.kujiBoard.member.id = :sellerId AND d.status = :status")
    Long countByKujiBoardMemberIdAndStatus(@org.springframework.data.repository.query.Param("sellerId") Long sellerId, @org.springframework.data.repository.query.Param("status") com.kuji.backend.domain.kuji.enums.DrawStatus status);
}
