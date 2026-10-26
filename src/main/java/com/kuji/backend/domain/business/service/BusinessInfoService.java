package com.kuji.backend.domain.business.service;

import com.kuji.backend.domain.business.dto.BusinessRegistrationRequest;
import com.kuji.backend.domain.business.dto.BusinessReviewRequest;
import com.kuji.backend.domain.member.dto.BusinessProfileResponse;
import com.kuji.backend.domain.member.entity.BusinessInfo;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.BusinessStatus;
import com.kuji.backend.domain.member.enums.RoleType;
import com.kuji.backend.domain.member.repository.BusinessInfoRepository;
import com.kuji.backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BusinessInfoService {
    private final BusinessInfoRepository businessInfoRepository;
    private final MemberRepository memberRepository;
    private final BusinessLicenseStorage licenseStorage;

    public static String validateRegistration(String companyName, String businessNumber, String ceoName, String address) {
        if (companyName == null || companyName.isBlank() || companyName.length() > 100 ||
                ceoName == null || ceoName.isBlank() || ceoName.length() > 50 ||
                address == null || address.isBlank() || address.length() > 255) {
            throw new IllegalArgumentException("상호명, 대표자명, 사업장 주소를 올바르게 입력해 주세요.");
        }
        String number = businessNumber == null ? "" : businessNumber.replace("-", "").trim();
        if (!number.matches("[0-9]{10}")) throw new IllegalArgumentException("사업자등록번호 10자리를 확인해 주세요.");
        int[] weights = {1,3,7,1,3,7,1,3,5};
        int sum = 0;
        for (int i = 0; i < 9; i++) sum += (number.charAt(i) - '0') * weights[i];
        sum += ((number.charAt(8) - '0') * 5) / 10;
        if ((10 - sum % 10) % 10 != number.charAt(9) - '0') throw new IllegalArgumentException("사업자등록번호를 확인해 주세요.");
        return number;
    }

    @Transactional
    public Long registerBusiness(Long memberId, BusinessRegistrationRequest request, MultipartFile license) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new AccessDeniedException("회원 정보를 확인해 주세요."));
        if (member.getRole() != RoleType.BIZ) throw new AccessDeniedException("사업자 계정으로 로그인해 주세요.");
        String number = validateRegistration(request.companyName(), request.businessNumber(), request.representativeName(), request.businessAddress());
        BusinessInfo existing = businessInfoRepository.findByIdWithLock(memberId).orElse(null);
        if (existing != null && existing.getStatus() == BusinessStatus.APPROVED) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 승인된 사업자입니다.");
        if ((existing == null || !number.equals(existing.getBusinessNumber())) && businessInfoRepository.existsByBusinessNumber(number)) {
            throw new IllegalArgumentException("이미 등록된 사업자등록번호입니다.");
        }
        String key = licenseStorage.store(license);
        if (existing == null) {
            existing = BusinessInfo.builder().member(member).companyName(request.companyName().trim()).businessNumber(number)
                    .ceoName(request.representativeName().trim()).businessAddress(request.businessAddress().trim()).licenseImageUrl(key).build();
            member.registerBusinessInfo(existing);
            businessInfoRepository.save(existing);
        } else {
            String oldKey = existing.getLicenseImageUrl();
            existing.resubmit(request.companyName().trim(), number, request.representativeName().trim(), request.businessAddress().trim(), key);
            licenseStorage.deleteAfterCommit(oldKey);
        }
        return memberId;
    }

    @Transactional(readOnly = true)
    public BusinessInfo getAccessibleBusiness(Long requesterId, Long memberId) {
        Member requester = memberRepository.findById(requesterId).orElseThrow(() -> new AccessDeniedException("로그인이 필요합니다."));
        if (!memberId.equals(requesterId) && requester.getRole() != RoleType.ADMIN) throw new AccessDeniedException("사업자 정보를 확인할 권한이 없습니다.");
        return businessInfoRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("등록된 사업자 정보가 없습니다."));
    }

    @Transactional
    public BusinessProfileResponse review(Long reviewerId, Long memberId, BusinessReviewRequest request) {
        Member reviewer = memberRepository.findById(reviewerId).orElseThrow(() -> new AccessDeniedException("로그인이 필요합니다."));
        if (reviewer.getRole() != RoleType.ADMIN) throw new AccessDeniedException("관리자만 사업자를 심사할 수 있습니다.");
        BusinessInfo info = businessInfoRepository.findByIdWithLock(memberId).orElseThrow(() -> new IllegalArgumentException("사업자 정보를 찾을 수 없습니다."));
        if (info.getStatus() != BusinessStatus.PENDING) throw new ResponseStatusException(HttpStatus.CONFLICT, "승인 대기 중인 신청만 심사할 수 있습니다.");
        if (request.status() == BusinessStatus.APPROVED) {
            if (!licenseStorage.isStored(info.getLicenseImageUrl())) throw new IllegalArgumentException("등록증을 제출한 후 승인할 수 있습니다.");
            info.approve();
        } else if (request.status() == BusinessStatus.REJECTED) {
            if (request.rejectReason() == null || request.rejectReason().isBlank() || request.rejectReason().length() > 255) {
                throw new IllegalArgumentException("반려 사유를 255자 이내로 입력해 주세요.");
            }
            info.reject(request.rejectReason().trim());
        } else throw new IllegalArgumentException("승인 또는 반려를 선택해 주세요.");
        return BusinessProfileResponse.from(info);
    }
}
