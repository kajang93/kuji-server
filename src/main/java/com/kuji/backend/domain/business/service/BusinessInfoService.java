package com.kuji.backend.domain.business.service;

import com.kuji.backend.domain.business.dto.BusinessRegistrationRequest;
import com.kuji.backend.domain.member.entity.BusinessInfo;
import com.kuji.backend.domain.member.repository.BusinessInfoRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessInfoService {

    private final BusinessInfoRepository businessInfoRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Long registerBusiness(String email, BusinessRegistrationRequest request) {
        // 1. 토큰에서 꺼낸 이메일로 진짜 회원 찾기
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));

        // 2. 이미 사업자를 등록한 사람인지 방어 로직! (1:1 관계니까)
        if (businessInfoRepository.existsById(member.getId())) {
            throw new IllegalArgumentException("이미 사업자가 등록된 회원입니다.");
        }

        // 3. 사업자 정보 생성 및 저장
        BusinessInfo businessInfo = request.toEntity(member);
        return businessInfoRepository.save(businessInfo).getMember().getId();
    }
}