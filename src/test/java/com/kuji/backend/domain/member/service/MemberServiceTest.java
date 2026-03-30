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
                LocalDate.of(1995, 5, 5),
                true, true, false);

        // when: 서비스의 가입 메서드 실행
        Long savedMemberId = memberService.signUp(request, SocialType.LOCAL, null);

        // then: ID가 잘 나왔는지, DB에 진짜 그 이름으로 들어갔는지 확인
        assertThat(savedMemberId).isNotNull();

        Member savedMember = memberRepository.findById(savedMemberId).orElseThrow();
        assertThat(savedMember.getNickname()).isEqualTo("쿠지뉴비");
        assertThat(savedMember.getEmail()).isEqualTo("new_user@kuji.com");
    }

    @Test
    @DisplayName("이미 가입된 이메일로 다시 가입을 시도하면 예외가 발생해야 한다")
    void signUpFail_DuplicateEmail() {
        // given: 1. 먼저 DB에 기존 회원을 한 명 가입시켜 둡니다.
        SignUpRequest request1 = new SignUpRequest(
                "duplicate@kuji.com", "pass1", "기존유저",
                LocalDate.of(1990, 1, 1), true, true, false);
        memberService.signUp(request1, SocialType.LOCAL, null);

        // given: 2. 똑같은 이메일을 쓰는 뻔뻔한(?) 두 번째 가입 요청 준비
        SignUpRequest request2 = new SignUpRequest(
                "duplicate@kuji.com", "pass2", "뻔뻔한유저",
                LocalDate.of(1999, 9, 9), true, true, false);

        // when & then: 두 번째 가입을 시도했을 때, 우리가 의도한 에러가 팡! 터져야 성공
        // 💡 실무 팁: 에러가 터지는 걸 테스트할 때는 assertThatThrownBy를 씁니다.
        assertThatThrownBy(() -> memberService.signUp(request2, SocialType.LOCAL, null))
                .isInstanceOf(IllegalArgumentException.class) // 우리가 던진 그 에러 클래스인지?
                .hasMessage("이미 가입된 이메일입니다."); // 우리가 적은 그 메시지가 맞는지?
    }
}