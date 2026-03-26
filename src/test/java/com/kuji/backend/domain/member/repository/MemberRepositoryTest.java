package com.kuji.backend.domain.member.repository;

import com.kuji.backend.domain.member.entity.Member;
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
class MemberRepositoryTest {

    @Autowired
    MemberRepository memberRepository;

    @Test
    @DisplayName("회원이 정상적으로 DB에 저장되고 조회되어야 한다")
    void saveAndFindMember() {
        // 1. given (준비)
        Member newMember = Member.builder()
                .role(RoleType.USER)
                .socialType(SocialType.KAKAO)
                .socialId("kakao_12345678")
                .email("test@kakao.com")
                .nickname("테스트유저")
                .birthDate(LocalDate.of(1993, 1, 1))
                .isTermsAgreed(true)
                .isPrivacyAgreed(true)
                .isMarketingAgreed(false)
                .build();

        // 2. when (실행)
        Member savedMember = memberRepository.save(newMember);

        // 3. then (검증)
        Member foundMember = memberRepository.findById(savedMember.getId()).orElseThrow();

        assertThat(foundMember.getNickname()).isEqualTo("테스트유저");
        assertThat(foundMember.getEmail()).isEqualTo("test@kakao.com");
        assertThat(foundMember.getPoint()).isEqualTo(0);
        
        assertThat(foundMember.getCreatedAt()).isNotNull();
        System.out.println("가입 시간: " + foundMember.getCreatedAt());
    }
}
