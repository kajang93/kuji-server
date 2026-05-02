package com.kuji.backend.domain.kuji.repository;

import com.kuji.backend.domain.kuji.entity.KujiItemImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KujiItemImageRepository extends JpaRepository<KujiItemImage, Long> {
    List<KujiItemImage> findAllByKujiItemIdOrderBySequenceAsc(Long kujiItemId);
}
