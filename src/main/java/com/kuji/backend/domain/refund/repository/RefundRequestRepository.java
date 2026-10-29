// 환불 요청의 소유자별 조회와 검토 잠금 및 중복 여부를 조회합니다.
package com.kuji.backend.domain.refund.repository;

import com.kuji.backend.domain.refund.entity.RefundRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    Optional<RefundRequest> findByActiveTargetKey(String activeTargetKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundRequest r where r.id = :id")
    Optional<RefundRequest> findByIdWithLock(@Param("id") Long id);
    @EntityGraph(attributePaths = "member")
    Slice<RefundRequest> findByMemberId(Long memberId, Pageable pageable);
    @EntityGraph(attributePaths = "member")
    Slice<RefundRequest> findByStatus(RefundRequest.Status status, Pageable pageable);
    @EntityGraph(attributePaths = "member")
    Slice<RefundRequest> findAllBy(Pageable pageable);
}
