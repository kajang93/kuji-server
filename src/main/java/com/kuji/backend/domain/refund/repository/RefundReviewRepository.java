// 환불 검토 이력을 요청별로 추가 보관합니다.
package com.kuji.backend.domain.refund.repository;

import com.kuji.backend.domain.refund.entity.RefundReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundReviewRepository extends JpaRepository<RefundReview, Long> {}
