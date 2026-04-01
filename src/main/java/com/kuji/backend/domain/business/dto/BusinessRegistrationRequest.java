package com.kuji.backend.domain.business.dto;

import com.kuji.backend.domain.member.entity.BusinessInfo;
import com.kuji.backend.domain.member.entity.Member;

public record BusinessRegistrationRequest(
        String companyName,
        String businessNumber,
        String representativeName) {
    // 💡 DTO를 Entity로 바꿔주는 마법! (이때 주인인 Member를 같이 엮어줍니다)
    public BusinessInfo toEntity(Member member) {
        return BusinessInfo.builder()
                .member(member) // @MapsId 덕분에 PK(ID)가 자동으로 공유됩니다!
                .companyName(this.companyName)
                .businessNumber(this.businessNumber)
                .ceoName(this.representativeName)
                .build();
    }
}