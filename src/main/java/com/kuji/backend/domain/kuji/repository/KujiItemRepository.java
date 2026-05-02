package com.kuji.backend.domain.kuji.repository;

import com.kuji.backend.domain.kuji.entity.KujiItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KujiItemRepository extends JpaRepository<KujiItem, Long> {
    List<KujiItem> findAllByKujiBoardIdOrderByGradeAsc(Long kujiBoardId);
}
