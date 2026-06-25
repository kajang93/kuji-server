package com.kuji.backend.domain.admin.service;

import com.kuji.backend.domain.member.entity.BusinessInfo;
import com.kuji.backend.domain.member.repository.BusinessInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final BusinessInfoRepository businessInfoRepository;

    @Transactional
    public void updateBusinessFeeRate(Long memberId, Integer feeRate) {
        BusinessInfo businessInfo = businessInfoRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("사업자 정보를 찾을 수 없습니다."));
        businessInfo.updateBaseFeeRate(feeRate);
    }
}
