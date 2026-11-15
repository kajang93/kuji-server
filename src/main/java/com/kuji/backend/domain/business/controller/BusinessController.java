package com.kuji.backend.domain.business.controller;

import com.kuji.backend.domain.business.dto.BusinessRegistrationRequest;
import com.kuji.backend.domain.business.dto.BusinessReviewRequest;
import com.kuji.backend.domain.business.service.BusinessInfoService;
import com.kuji.backend.domain.business.service.BusinessLicenseStorage;
import com.kuji.backend.domain.member.dto.BusinessProfileResponse;
import com.kuji.backend.domain.member.entity.BusinessInfo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/business")
public class BusinessController {
    private static final Logger auditLogger = LoggerFactory.getLogger("com.kuji.backend.audit");
    private final BusinessInfoService businessInfoService;
    private final BusinessLicenseStorage licenseStorage;
    private final com.kuji.backend.domain.business.service.BusinessStatusVerifier statusVerifier;
    // 가입 전(비로그인)에도 부르는 공개 API라 IP당 1분 20회로 제한한다
    private final java.util.Map<String, java.util.Deque<Long>> verifyCalls = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 사업자등록번호 휴·폐업 확인 (국세청 상태조회). 가입 화면의 '확인' 버튼에서 호출한다.
     */
    @PostMapping("/verify-number")
    public ResponseEntity<java.util.Map<String, Object>> verifyNumber(@RequestBody java.util.Map<String, String> body,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        String ip = java.util.Optional.ofNullable(httpRequest.getHeader("X-Forwarded-For")).map(v -> v.split(",")[0].trim()).orElse(httpRequest.getRemoteAddr());
        long now = System.currentTimeMillis();
        java.util.Deque<Long> calls = verifyCalls.computeIfAbsent(ip, key -> new java.util.ArrayDeque<>());
        synchronized (calls) {
            while (!calls.isEmpty() && now - calls.peekFirst() > 60_000) calls.pollFirst();
            if (calls.size() >= 20) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 확인해 주세요.");
            calls.addLast(now);
        }
        if (verifyCalls.size() > 10_000) verifyCalls.clear();
        String number = BusinessInfoService.validateNumberFormat(body == null ? null : body.get("businessNumber"));
        var result = statusVerifier.check(number);
        return ResponseEntity.ok(java.util.Map.of(
                "status", result.status().name(),
                "canRegister", result.canRegister() || result.status() == com.kuji.backend.domain.business.service.BusinessStatusVerifier.Status.UNAVAILABLE && !statusVerifier.configured(),
                "verifiedByNts", statusVerifier.configured() && result.status() != com.kuji.backend.domain.business.service.BusinessStatusVerifier.Status.UNAVAILABLE,
                "message", result.message()));
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Long> registerBusiness(@AuthenticationPrincipal Long memberId,
            @RequestPart("data") BusinessRegistrationRequest request, @RequestPart("license") MultipartFile license) {
        return ResponseEntity.ok(businessInfoService.registerBusiness(memberId, request, license));
    }

    @GetMapping("/{memberId}")
    public BusinessProfileResponse getBusiness(@AuthenticationPrincipal Long requesterId, @PathVariable("memberId") Long memberId) {
        return BusinessProfileResponse.from(businessInfoService.getAccessibleBusiness(requesterId, memberId));
    }

    @GetMapping("/{memberId}/license")
    public ResponseEntity<Resource> downloadLicense(@AuthenticationPrincipal Long requesterId, @PathVariable("memberId") Long memberId) {
        BusinessInfo info = businessInfoService.getAccessibleBusiness(requesterId, memberId);
        Resource resource = licenseStorage.open(info.getLicenseImageUrl());
        auditLogger.info("사업자 등록증 열람. requesterId={}, businessMemberId={}", requesterId, memberId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"business-license." + resource.getFilename().replaceAll(".*\\.", "") + "\"")
                .contentType(licenseStorage.contentType(info.getLicenseImageUrl())).body(resource);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{memberId}/review")
    public BusinessProfileResponse review(@AuthenticationPrincipal Long reviewerId, @PathVariable("memberId") Long memberId,
            @RequestBody BusinessReviewRequest request) {
        return businessInfoService.review(reviewerId, memberId, request);
    }
}
