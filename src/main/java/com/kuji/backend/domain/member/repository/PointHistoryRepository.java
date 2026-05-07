package com.kuji.backend.domain.member.repository;

import com.kuji.backend.domain.member.entity.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {
    List<PointHistory> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
}
