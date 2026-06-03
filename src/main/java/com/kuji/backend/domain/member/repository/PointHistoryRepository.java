package com.kuji.backend.domain.member.repository;

import com.kuji.backend.domain.member.entity.PointHistory;
import com.kuji.backend.domain.member.enums.PointType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {
    List<PointHistory> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    // [Admin] 전체 누적 금액 산출
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PointHistory p WHERE p.type = :type")
    Long sumAmountByType(@Param("type") PointType type);

    // [Admin] 일별 금액 합산 (최근 N일) - PostgreSQL Date Cast
    @Query("SELECT CAST(p.createdAt AS date) as date, SUM(p.amount) " +
           "FROM PointHistory p WHERE p.type = :type AND p.createdAt >= :startDate " +
           "GROUP BY CAST(p.createdAt AS date) ORDER BY date ASC")
    List<Object[]> getDailySumByType(@Param("type") PointType type, @Param("startDate") LocalDateTime startDate);

    // [Seller] 총 누적 판매 금액 산출
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PointHistory p " +
           "JOIN p.drawHistory d JOIN d.kujiBoard b " +
           "WHERE p.type = :type AND b.member.id = :sellerId")
    Long sumTotalSalesBySellerId(@Param("sellerId") Long sellerId, @Param("type") PointType type);

    // [Seller] 특정 기간 판매 금액 산출 (정산용)
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PointHistory p " +
           "JOIN p.drawHistory d JOIN d.kujiBoard b " +
           "WHERE p.type = :type AND b.member.id = :sellerId " +
           "AND p.createdAt >= :startDate AND p.createdAt < :endDate")
    Long sumSalesBySellerIdAndDateRange(@Param("sellerId") Long sellerId, @Param("type") PointType type, 
                                        @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // [Seller] 일별 판매 금액 합산 (최근 N일)
    @Query("SELECT CAST(p.createdAt AS date) as date, SUM(p.amount) " +
           "FROM PointHistory p JOIN p.drawHistory d JOIN d.kujiBoard b " +
           "WHERE p.type = :type AND b.member.id = :sellerId AND p.createdAt >= :startDate " +
           "GROUP BY CAST(p.createdAt AS date) ORDER BY date ASC")
    List<Object[]> getDailySalesBySellerId(@Param("sellerId") Long sellerId, @Param("type") PointType type, @Param("startDate") LocalDateTime startDate);
}
