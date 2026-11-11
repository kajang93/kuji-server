// 공개 집계 접수와 관리자 전용 가입 흐름 보고서를 구분한다.
package com.kuji.backend.domain.analytics;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FunnelController {
    private final FunnelService service;
    @PostMapping("/api/analytics/funnel") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@Valid @RequestBody FunnelService.Input input) { service.record(input); }
    @GetMapping(value="/api/admin/analytics/signup-funnel", produces="application/json") @PreAuthorize("hasRole('ADMIN')")
    public FunnelService.Report report(@RequestParam(defaultValue="7") int days) { return service.report(days); }
}
