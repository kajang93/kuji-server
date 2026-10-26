// 관리자가 사업자 심사 결과와 반려 사유를 전달합니다.
package com.kuji.backend.domain.business.dto;

import com.kuji.backend.domain.member.enums.BusinessStatus;

public record BusinessReviewRequest(BusinessStatus status, String rejectReason) {}
