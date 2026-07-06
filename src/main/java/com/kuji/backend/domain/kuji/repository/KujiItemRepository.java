package com.kuji.backend.domain.kuji.repository;

import com.kuji.backend.domain.kuji.entity.KujiItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KujiItemRepository extends JpaRepository<KujiItem, Long> {
    List<KujiItem> findAllByKujiBoardIdOrderByGradeAsc(Long kujiBoardId);
    void deleteAllByKujiBoardId(Long kujiBoardId);

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
