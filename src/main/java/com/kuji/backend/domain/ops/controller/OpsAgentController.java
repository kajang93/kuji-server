package com.kuji.backend.domain.ops.controller;

import com.kuji.backend.domain.ops.service.OpsAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ops/incident")
@RequiredArgsConstructor
public class OpsAgentController {

    private final OpsAgentService opsAgentService;

    /**
     * 슬랙에서 [승인(Approve)] 버튼을 클릭했을 때 호출되는 엔드포인트
     */
    @GetMapping("/{incidentId}/approve")
    public ResponseEntity<Map<String, String>> approveIncident(
            @PathVariable String incidentId,
            @RequestParam String token) {
        String resultMessage = opsAgentService.approveIncident(incidentId, token);
        return ResponseEntity.ok(Map.of(
                "status", resultMessage.contains("✅") ? "SUCCESS" : "ERROR",
                "message", resultMessage
        ));
    }

    /**
     * 슬랙에서 [승인 거절(Reject)] 버튼을 클릭했을 때 호출되는 엔드포인트
     */
    @GetMapping("/{incidentId}/reject")
    public ResponseEntity<Map<String, String>> rejectIncident(
            @PathVariable String incidentId,
            @RequestParam String token) {
        String resultMessage = opsAgentService.rejectIncident(incidentId, token);
        return ResponseEntity.ok(Map.of(
                "status", resultMessage.contains("❌") ? "SUCCESS" : "ERROR",
                "message", resultMessage
        ));
    }
    
    /**
     * 테스트용: 강제로 장애 상황을 만들어서 에이전트 트리거
     */
    @GetMapping("/test-trigger")
    public ResponseEntity<String> triggerTestAnomaly() {
        String dummyErrorLog = "java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms. DB Lock detected on table 'orders'.";
        opsAgentService.analyzeAndReportAnomaly(dummyErrorLog);
        return ResponseEntity.ok("테스트용 에러 로그가 Ops Agent로 전달되었습니다. 슬랙을 확인해 주세요.");
    }
}
