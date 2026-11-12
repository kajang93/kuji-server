package com.kuji.backend.domain.kuji.repository;

import com.kuji.backend.domain.kuji.entity.KujiBoard;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.kuji.backend.domain.kuji.enums.BoardStatus;
import java.util.List;
import java.util.Optional;

@Repository
public interface KujiBoardRepository extends JpaRepository<KujiBoard, Long> {
    List<KujiBoard> findByMemberId(Long memberId);
    Long countByMemberId(Long memberId);
    List<KujiBoard> findByStatus(BoardStatus status);

    // 판매를 중지해도 이미 공개되어 구매된 상품은 열람할 수 있고, 미공개 초안은 숨긴다.
    @Query("select b from KujiBoard b where b.previewOnly = false and " +
            "(b.status = com.kuji.backend.domain.kuji.enums.BoardStatus.ACTIVE or " +
            "(b.status = com.kuji.backend.domain.kuji.enums.BoardStatus.PREPARING and " +
            "exists (select d.id from DrawHistory d where d.kujiBoard = b))) " +
            "order by b.createdAt desc, b.id desc")
    List<KujiBoard> findPublicBoards();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from KujiBoard b where b.id = :id")
    Optional<KujiBoard> findByIdWithLock(@Param("id") Long id);
}
