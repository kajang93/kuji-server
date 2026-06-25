package com.kuji.backend.domain.admin.controller;

import com.kuji.backend.domain.member.dto.AdminMemberResponse;
import com.kuji.backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kuji.backend.domain.admin.service.AdminService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final MemberRepository memberRepository;
    private final AdminService adminService;

    /**
     * 관리자: 전체 회원 목록 조회
     */
    @GetMapping("/members")
    public ResponseEntity<List<AdminMemberResponse>> getAllMembers() {
        // 실제 서비스에서는 Paging 처리가 필요하지만 데모를 위해 전체 조회로 구현합니다.
        List<AdminMemberResponse> members = memberRepository.findAll().stream()
                .map(AdminMemberResponse::from)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(members);
    }

    public record UpdateFeeRateRequest(Integer feeRate) {}

    /**
     * 관리자: 사업자 수수료율 변경
     */
    @PutMapping("/members/{id}/fee-rate")
    public ResponseEntity<Void> updateFeeRate(
            @PathVariable Long id,
            @RequestBody UpdateFeeRateRequest request) {
        adminService.updateBusinessFeeRate(id, request.feeRate());
        return ResponseEntity.ok().build();
    }
}
