package com.kuji.backend.domain.member.dto;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.RoleType;
import com.kuji.backend.domain.member.enums.SocialType;

import java.time.LocalDate;

// 💡 Record를 사용하면 @Getter, 생성자, toString() 등을 알아서 다 만들어줍니다. 
// 괄호 안에 있는 변수들은 자동으로 불변(final) 처리가 되어 데이터가 중간에 변조될 위험이 없습니다.
public record SignUpRequest(
        String email,
        String password, // 소셜 로그인의 경우 비밀번호가 없을 수 있으므로 nullable 처리
        String nickname,
        LocalDate birthDate,
        Boolean isTermsAgreed,
        Boolean isPrivacyAgreed,
        Boolean isMarketingAgreed) {
    // 💡 실무 팁: Service 계층을 깔끔하게 유지하기 위해 DTO -> Entity 변환 메서드를 DTO 안에 둡니다.
    public Member toEntity(SocialType socialType, String socialId) {
        return Member.builder()
                .role(RoleType.USER) // 가입 시 기본 권한은 일반 유저(USER)
                .socialType(socialType)
                .socialId(socialId)
                .email(this.email)
                .password(this.password)
                .nickname(this.nickname)
                .birthDate(this.birthDate)
                .isTermsAgreed(this.isTermsAgreed)
                .isPrivacyAgreed(this.isPrivacyAgreed)
                .isMarketingAgreed(this.isMarketingAgreed)
                .build();
    }
}