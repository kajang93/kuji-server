package com.kuji.backend.domain.member.service;

import com.kuji.backend.domain.member.dto.SignUpRequest;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.SocialType;
import com.kuji.backend.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MemberServiceTest {

    @Autowired
    MemberService memberService;

    @Autowired
    MemberRepository memberRepository;

    @Test
    @DisplayName("새로운 이메일로 가입하면 정상적으로 회원 ID가 반환되어야 한다")
    void signUpSuccess() {
        // given: 가입할 회원 데이터 (DTO) 준비
        SignUpRequest request = new SignUpRequest(
                "new_user@kuji.com",
                "password123!",
                "쿠지뉴비",
                "010-1234-5678", // phoneNumber
                LocalDate.of(1995, 5, 5),
                null,   // role
                null,   // businessNumber
                null,   // companyName
                null,   // ceoName
                true, true, false);

        // when: 서비스의 가입 메서드 실행
        Long savedMemberId = memberService.signUp(request, SocialType.LOCAL, null);

        // then: ID가 잘 나왔는지, DB에 진짜 그 이름으로 들어갔는지 확인
        assertThat(savedMemberId).isNotNull();

        Member savedMember = memberRepository.findById(savedMemberId).orElseThrow();
        assertThat(savedMember.getNickname()).isEqualTo("쿠지뉴비");
        assertThat(savedMember.getEmail()).isEqualTo("new_user@kuji.com");
        assertThat(savedMember.getPhoneNumber()).isEqualTo("010-1234-5678");
    }

    @Test
    @DisplayName("이미 가입된 이메일로 다시 가입을 시도하면 예외가 발생해야 한다")
    void signUpFail_DuplicateEmail() {
        // given: 1. 먼저 DB에 기존 회원을 한 명 가입시켜 둡니다.
        SignUpRequest request1 = new SignUpRequest(
                "duplicate@kuji.com", "pass1", "기존유저", "010-1111-2222",
                LocalDate.of(1990, 1, 1),
                null, null, null, null,
                true, true, false);
        memberService.signUp(request1, SocialType.LOCAL, null);

        // given: 2. 똑같은 이메일을 쓰는 두 번째 가입 요청 준비
        SignUpRequest request2 = new SignUpRequest(
                "duplicate@kuji.com", "pass2", "뻔뻔한유저", "010-3333-4444",
                LocalDate.of(1999, 9, 9),
                null, null, null, null,
                true, true, false);

        // when & then: 예외 발생 확인
        assertThatThrownBy(() -> memberService.signUp(request2, SocialType.LOCAL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 가입된 이메일입니다.");
    }

    @Test
    @DisplayName("이메일 중복 확인 기능을 테스트한다")
    void testIsEmailExist() {
        // given
        SignUpRequest request = new SignUpRequest(
                "exist@kuji.com", "pass", "유저", "010-1234-1234",
                LocalDate.of(2000, 1, 1),
                null, null, null, null, true, true, false);
        memberService.signUp(request, SocialType.LOCAL, null);

        // when & then
        assertThat(memberService.isEmailExist("exist@kuji.com")).isTrue();
        assertThat(memberService.isEmailExist("notexist@kuji.com")).isFalse();
    }

    @Test
    @DisplayName("전화번호로 아이디(이메일)를 찾고 마스킹 처리되어야 한다")
    void testFindId() {
        // given
        SignUpRequest request = new SignUpRequest(
                "findid@kuji.com", "pass", "유저", "010-8888-7777",
                LocalDate.of(2000, 1, 1),
                null, null, null, null, true, true, false);
        memberService.signUp(request, SocialType.LOCAL, null);

        // when
        String maskedEmail = memberService.findId("010-8888-7777");

        // then
        assertThat(maskedEmail).isEqualTo("fin****@kuji.com");
    }

    @Test
    @DisplayName("이메일과 전화번호가 일치하면 임시 비밀번호로 초기화된다")
    void testResetPassword() {
        // given
        SignUpRequest request = new SignUpRequest(
                "resetpw@kuji.com", "old_pass", "유저", "010-7777-6666",
                LocalDate.of(2000, 1, 1),
                null, null, null, null, true, true, false);
        Long memberId = memberService.signUp(request, SocialType.LOCAL, null);

        // when
        memberService.resetPassword("resetpw@kuji.com", "010-7777-6666");

        // then
        // MemberService 내부에서 비밀번호가 temp1234! 로 암호화되어 저장됨을 검증
        // 실제로 로그인 시도(login 메서드)를 해서 성공하는지 확인
        com.kuji.backend.domain.member.dto.LoginRequest loginReq = 
                new com.kuji.backend.domain.member.dto.LoginRequest("resetpw@kuji.com", "temp1234!");
        String token = memberService.login(loginReq);
        assertThat(token).isNotBlank();
    }
}