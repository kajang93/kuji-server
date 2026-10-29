package com.kuji.backend.domain.kuji.repository;

import com.kuji.backend.domain.kuji.entity.KujiItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KujiItemRepository extends JpaRepository<KujiItem, Long> {
    List<KujiItem> findAllByKujiBoardIdOrderByGradeAsc(Long kujiBoardId);
    void deleteAllByKujiBoardId(Long kujiBoardId);

    interface ItemNameSummary {
        String getName();
        Long getUsageCount();
    }

    @org.springframework.data.jpa.repository.Query("SELECT i.name AS name, COUNT(i) AS usageCount " +
            "FROM KujiItem i " +
            "WHERE UPPER(i.grade) = UPPER(:grade) AND i.name IS NOT NULL AND i.name <> '' " +
            "GROUP BY i.name " +
            "ORDER BY COUNT(i) DESC, MAX(i.updatedAt) DESC")
    List<ItemNameSummary> findTopNamesByGrade(@Param("grade") String grade, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT i.totalQty FROM KujiItem i " +
            "WHERE UPPER(i.grade) = UPPER(:grade) AND i.totalQty IS NOT NULL")
    List<Integer> findTotalQuantitiesByGrade(@Param("grade") String grade);

    // 목록 화면용 재고 집계 (보드별 3쿼리 → 전체 1쿼리)
    interface BoardStockSummary {
        Long getBoardId();
        Long getTotalQty();
        Long getRemainQty();
        Long getGradeCount();
        Long getLastPrizeQty();
        Long getLastPrizeRemainQty();
    }

    String LAST_GRADE_MATCH = "UPPER(REPLACE(REPLACE(REPLACE(TRIM(i.grade), ' ', ''), '-', ''), '_', '')) " +
            "IN ('LAST', 'LASTONE', 'LAST상', '라스트', '라스트상', '라스트원')";

    @org.springframework.data.jpa.repository.Query("SELECT i.kujiBoard.id AS boardId, " +
            "COALESCE(SUM(CASE WHEN " + LAST_GRADE_MATCH + " THEN 0 ELSE i.totalQty END), 0) AS totalQty, " +
            "COALESCE(SUM(CASE WHEN " + LAST_GRADE_MATCH + " THEN 0 ELSE i.remainQty END), 0) AS remainQty, " +
            "COUNT(i) AS gradeCount, " +
            "COALESCE(SUM(CASE WHEN " + LAST_GRADE_MATCH + " THEN i.totalQty ELSE 0 END), 0) AS lastPrizeQty, " +
            "COALESCE(SUM(CASE WHEN " + LAST_GRADE_MATCH + " THEN i.remainQty ELSE 0 END), 0) AS lastPrizeRemainQty " +
            "FROM KujiItem i WHERE i.kujiBoard.id IN :boardIds GROUP BY i.kujiBoard.id")
    List<BoardStockSummary> summarizeByBoardIds(@org.springframework.data.repository.query.Param("boardIds") List<Long> boardIds);
}
