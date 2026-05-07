package com.kuji.backend.domain.kuji.repository;

import com.kuji.backend.domain.kuji.entity.KujiBoard;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KujiBoardRepository extends JpaRepository<KujiBoard, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from KujiBoard b where b.id = :id")
    Optional<KujiBoard> findByIdWithLock(@Param("id") Long id);
}
