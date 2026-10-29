// 금전 변경 없이 환불 접수와 관리자 검토 및 이력 보존을 처리합니다.
package com.kuji.backend.domain.refund.service;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.RoleType;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.refund.dto.RefundDtos;
import com.kuji.backend.domain.refund.entity.RefundRequest;
import com.kuji.backend.domain.refund.entity.RefundReview;
import com.kuji.backend.domain.refund.repository.RefundRequestRepository;
import com.kuji.backend.domain.refund.repository.RefundReviewRepository;
import com.kuji.backend.domain.refund.repository.RefundTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {
    private final MemberRepository members;
    private final RefundRequestRepository requests;
    private final RefundReviewRepository reviews;
    private final RefundTargetRepository targets;

    public RefundDtos.PageResponse<RefundDtos.Target> targets(Long memberId, RefundRequest.TargetType type, int page, int size) {
        requireMember(memberId);
        if (type == null) throw badRequest("환불 대상 종류를 선택해주세요.");
        validatePage(page, size);
        var content = targets.list(memberId, type, size + 1, page * size);
        return new RefundDtos.PageResponse<>(content.stream().limit(size).toList(), page, size, content.size() > size);
    }
    @Transactional
    public RefundDtos.Response create(Long memberId, RefundDtos.Create input) {
        if (input == null || input.targetType() == null || input.targetId() == null || input.targetId() < 1) {
            throw badRequest("환불 대상 정보를 확인해주세요.");
        }
        String reason = validateReason(input.reason(), 10);
        if (memberId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        Member member = members.findByIdWithLock(memberId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
        var target = targets.findOwned(memberId, input.targetType(), input.targetId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "환불 요청 대상 내역을 확인할 수 없습니다."));
        if (requests.findByActiveTargetKey(RefundRequest.key(input.targetType(), input.targetId())).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 접수되거나 검토 중인 환불 요청입니다. 내 요청 상태를 확인해주세요.");
        }
        RefundRequest request = new RefundRequest(member, target.targetType(), target.targetId(), target.title(),
                target.occurredAt(), target.targetAmount(), target.fulfillmentStatus(), reason);
        return RefundDtos.Response.from(requests.saveAndFlush(request));
    }
    public RefundDtos.PageResponse<RefundDtos.Response> mine(Long memberId, int page, int size) {
        requireMember(memberId);
        return responsePage(requests.findByMemberId(memberId, pageable(page, size)), page, size);
    }
    public RefundDtos.PageResponse<RefundDtos.Response> adminList(Long adminId, RefundRequest.Status status, int page, int size) {
        requireAdmin(adminId);
        PageRequest pageable = pageable(page, size);
        Slice<RefundRequest> result = status == null ? requests.findAllBy(pageable) : requests.findByStatus(status, pageable);
        return responsePage(result, page, size);
    }
    @Transactional
    public RefundDtos.Response review(Long adminId, Long requestId, RefundDtos.Review input) {
        requireAdmin(adminId);
        if (input == null || input.status() == null || input.status() == RefundRequest.Status.REQUESTED) {
            throw badRequest("검토 상태를 선택해주세요.");
        }
        String reason = validateReason(input.reason(), 5);
        if (requestId == null || requestId < 1) throw badRequest("환불 요청 번호를 확인해주세요.");
        RefundRequest request = requests.findByIdWithLock(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "환불 요청을 찾을 수 없습니다."));
        if (request.getStatus() == input.status() && reason.equals(request.getReviewReason())) {
            return RefundDtos.Response.from(request);
        }
        RefundRequest.Status previous = request.getStatus();
        try { request.review(input.status(), reason, adminId); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()); }
        reviews.save(new RefundReview(request, previous, adminId, reason));
        return RefundDtos.Response.from(request);
    }
    private Member requireMember(Long memberId) {
        if (memberId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        return members.findById(memberId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }
    private void requireAdmin(Long adminId) {
        if (requireMember(adminId).getRole() != RoleType.ADMIN) throw new AccessDeniedException("환불 요청을 검토할 권한이 없습니다.");
    }
    private String validateReason(String reason, int min) {
        String trimmed = reason == null ? "" : reason.strip();
        if (trimmed.length() < min || trimmed.length() > 1000) throw badRequest("사유는 " + min + "자 이상 1,000자 이하로 입력해주세요.");
        return trimmed;
    }
    private void validatePage(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 50) throw badRequest("페이지는 0~10,000, 조회 개수는 1~50으로 입력해주세요.");
    }
    private PageRequest pageable(int page, int size) {
        validatePage(page, size);
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
    private RefundDtos.PageResponse<RefundDtos.Response> responsePage(Slice<RefundRequest> result, int page, int size) {
        return new RefundDtos.PageResponse<>(result.getContent().stream().map(RefundDtos.Response::from).toList(), page, size, result.hasNext());
    }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
