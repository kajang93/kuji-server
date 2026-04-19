package com.kuji.backend.domain.kuji.repository;

import com.kuji.backend.domain.kuji.entity.KujiBoardImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KujiBoardImageRepository extends JpaRepository<KujiBoardImage, Long> {
    List<KujiBoardImage> findAllByKujiBoardIdOrderBySequenceAsc(Long kujiBoardId);
}
