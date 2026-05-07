package com.kuji.backend.domain.kuji.repository;

import com.kuji.backend.domain.kuji.entity.DrawHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DrawHistoryRepository extends JpaRepository<DrawHistory, Long> {
    List<DrawHistory> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
}
