package com.kuji.backend.domain.kuji.repository;

import com.kuji.backend.domain.kuji.entity.DrawHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import com.kuji.backend.domain.shipping.entity.Shipping;

@Repository
public interface DrawHistoryRepository extends JpaRepository<DrawHistory, Long> {
    List<DrawHistory> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<DrawHistory> findAllByShipping(Shipping shipping); // 배송 꾸러미별 상품 조회용 추가
    List<DrawHistory> findTop20ByOrderByCreatedAtDesc(); // 티커용 최신 20건 조회
}
