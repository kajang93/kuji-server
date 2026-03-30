package com.kuji.backend.domain.member.repository;

import com.kuji.backend.domain.member.entity.BusinessInfo;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.BusinessStatus;
import com.kuji.backend.domain.member.enums.RoleType;
import com.kuji.backend.domain.member.enums.SocialType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BusinessInfoRepositoryTest {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    BusinessInfoRepository businessInfoRepository;

    @Test
    @DisplayName("사업자 정보가 회원과 1:1 식별 관계로 잘 저장되어야 한다")
    void saveBusinessInfoWithMember() {
        // 1. given: 회원을 먼저 생성하고 DB에 저장 (사업자는 회원이 무조건 있어야 함!)
        Member newMember = Member.builder()
                .role(RoleType.BIZ) // 사업자 권한
                .socialType(SocialType.LOCAL)
                .email("biz@kuji.com")
                .nickname("쿠지사장님")
                .birthDate(LocalDate.of(1993, 5, 15))
                .isTermsAgreed(true)
                .isPrivacyAgreed(true)
                .isMarketingAgreed(false)
                .build();

        Member savedMember = memberRepository.save(newMember);

        // 2. given: 사업자 정보 생성 (방금 저장한 savedMember를 넣어줌)
        BusinessInfo newBusinessInfo = BusinessInfo.builder()
                .member(savedMember)
                .businessNumber("123-45-67890")
                .companyName("대박쿠지상점")
                .ceoName("경아") // 대표님 이름 세팅!
                .licenseImageUrl("https://image.url/license.png")
                .build();

        // 💡 객체지향의 정석: 양방향 연관관계 편의 메서드 호출
        savedMember.registerBusinessInfo(newBusinessInfo);

        // 3. when: 사업자 정보 저장
        BusinessInfo savedBusinessInfo = businessInfoRepository.save(newBusinessInfo);

        // 4. then: 검증의 시간!
        // 가장 중요한 테스트: 사업자 정보의 ID와 회원의 ID가 똑같은가? (@MapsId 작동 확인)
        assertThat(savedBusinessInfo.getId()).isEqualTo(savedMember.getId());

        // 데이터가 잘 들어갔는지 확인
        assertThat(savedBusinessInfo.getCompanyName()).isEqualTo("대박쿠지상점");
        assertThat(savedBusinessInfo.getStatus()).isEqualTo(BusinessStatus.PENDING); // 기본 상태가 대기(PENDING)인지 확인

        // 눈으로 보기 위한 로그 출력
        System.out.println("========================================");
        System.out.println("발급된 회원 ID: " + savedMember.getId());
        System.out.println("발급된 사업자 ID: " + savedBusinessInfo.getId());
        System.out.println("대표자명: " + savedBusinessInfo.getCeoName());
        System.out.println("========================================");
    }
}