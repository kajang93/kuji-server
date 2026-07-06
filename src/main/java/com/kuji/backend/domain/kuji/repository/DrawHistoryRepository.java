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
    List<DrawHistory> findTop20ByOrderByCreatedAtDesc(); // 티커용 최신 20건 조회 (기존)

    // 내 당첨 내역 조회 (연관 엔티티 fetch join으로 N+1 제거)
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT d FROM DrawHistory d " +
            "JOIN FETCH d.kujiBoard b " +
            "JOIN FETCH b.member bm " +
            "LEFT JOIN FETCH bm.businessInfo " +
            "JOIN FETCH d.kujiItem i " +
            "LEFT JOIN FETCH i.kujiItemImages " +
            "LEFT JOIN FETCH d.shipping " +
            "WHERE d.member.id = :memberId ORDER BY d.createdAt DESC")
    List<DrawHistory> findAllByMemberIdWithDetails(@org.springframework.data.repository.query.Param("memberId") Long memberId);

    // 티커용 상위 등상(A, B, C, Last) 최신 내역 조회 (fetch join으로 N+1 제거)
    @org.springframework.data.jpa.repository.Query("SELECT d FROM DrawHistory d " +
            "JOIN FETCH d.member JOIN FETCH d.kujiBoard JOIN FETCH d.kujiItem i " +
            "WHERE i.grade LIKE 'A%' OR i.grade LIKE 'B%' OR i.grade LIKE 'C%' OR UPPER(i.grade) LIKE '%LAST%' ORDER BY d.createdAt DESC")
    List<DrawHistory> findRecentHighGradeDraws(org.springframework.data.domain.Pageable pageable);

    // [Seller] 사업자의 쿠지판에서 발생한 배송 대기 등 특정 상태의 내역 건수 조회
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(d) FROM DrawHistory d WHERE d.kujiBoard.member.id = :sellerId AND d.status = :status")
    Long countByKujiBoardMemberIdAndStatus(@org.springframework.data.repository.query.Param("sellerId") Long sellerId, @org.springframework.data.repository.query.Param("status") com.kuji.backend.domain.kuji.enums.DrawStatus status);
    // [Admin] 특정 시점 이후 발생한 뽑기/결제 성공 건수 집계 (예: 오늘 하루 발생한 매출 건수)
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(d) FROM DrawHistory d WHERE d.createdAt >= :startDate")
    Long countDrawsCreatedAfter(@org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate);
}
