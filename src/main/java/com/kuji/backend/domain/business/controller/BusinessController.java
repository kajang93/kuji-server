package com.kuji.backend.domain.business.controller;

import com.kuji.backend.domain.business.dto.BusinessRegistrationRequest;
import com.kuji.backend.domain.business.dto.BusinessReviewRequest;
import com.kuji.backend.domain.business.service.BusinessInfoService;
import com.kuji.backend.domain.business.service.BusinessLicenseStorage;
import com.kuji.backend.domain.member.dto.BusinessProfileResponse;
import com.kuji.backend.domain.member.entity.BusinessInfo;
import lombok.RequiredArgsConstructor;
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
    private final BusinessInfoService businessInfoService;
    private final BusinessLicenseStorage licenseStorage;

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
