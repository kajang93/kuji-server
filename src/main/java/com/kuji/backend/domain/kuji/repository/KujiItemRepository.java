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
    }

    @org.springframework.data.jpa.repository.Query("SELECT i.kujiBoard.id AS boardId, " +
            "COALESCE(SUM(i.totalQty), 0) AS totalQty, " +
            "COALESCE(SUM(i.remainQty), 0) AS remainQty, " +
            "COUNT(i) AS gradeCount " +
            "FROM KujiItem i WHERE i.kujiBoard.id IN :boardIds GROUP BY i.kujiBoard.id")
    List<BoardStockSummary> summarizeByBoardIds(@org.springframework.data.repository.query.Param("boardIds") List<Long> boardIds);
}
