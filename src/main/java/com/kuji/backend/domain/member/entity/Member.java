package com.kuji.backend.domain.member.entity;

import com.kuji.backend.domain.member.enums.RoleType;
import com.kuji.backend.domain.member.enums.SocialType;
import com.kuji.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Entity
@Table(
    name = "member",
    uniqueConstraints = {
        @UniqueConstraint(name = "uc_member_email", columnNames = {"email"}),
        @UniqueConstraint(name = "uc_member_social", columnNames = {"social_type", "social_id"})
    },
    indexes = {
        @Index(name = "idx_member_email", columnList = "email"),
        @Index(name = "idx_member_social", columnList = "social_type, social_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // --- OAuth 및 권한 정보 ---
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM) // ★ PostgreSQL의 커스텀 Enum(role_type)과 매핑하기 위해 추가
    @Column(nullable = false, length = 20)
    private RoleType role;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_type", nullable = false, length = 20)
    private SocialType socialType;

    @Column(name = "social_id") // 소셜 로그인 시 발급받는 고유 번호
    private String socialId;

    // --- 기본 회원 정보 ---
    @Column(nullable = false, length = 100)
    private String email;

    @Column(length = 255) // 소셜 로그인은 비밀번호가 없을 수 있으므로 nullable
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_url", length = 255)
    private String profileImageUrl;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(nullable = false)
    @ColumnDefault("0") // DB 기본값 0
    private Integer point = 0; // 자바 단에서도 0으로 초기화

    // --- 약관 동의 정보 ---
    @Column(name = "is_terms_agreed", nullable = false)
    private Boolean isTermsAgreed;

    @Column(name = "is_privacy_agreed", nullable = false)
    private Boolean isPrivacyAgreed;

    @Column(name = "is_marketing_agreed", nullable = false)
    private Boolean isMarketingAgreed;

    @Builder
    public Member(RoleType role, SocialType socialType, String socialId, String email, 
                  String password, String nickname, String profileImageUrl, LocalDate birthDate, 
                  Boolean isTermsAgreed, Boolean isPrivacyAgreed, Boolean isMarketingAgreed) {
        this.role = role;
        this.socialType = socialType;
        this.socialId = socialId;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.birthDate = birthDate;
        this.isTermsAgreed = isTermsAgreed;
        this.isPrivacyAgreed = isPrivacyAgreed;
        this.isMarketingAgreed = isMarketingAgreed;
        this.point = 0; // 가입 시 포인트는 무조건 0
    }

    // 💡 mappedBy를 통해 연관관계의 주인이 BusinessInfo임을 명시
    // cascade = CascadeType.ALL: 회원이 삭제되면 사업자 정보도 같이 삭제
    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private BusinessInfo businessInfo;

    public void addPoint(int amount) {
        this.point += amount;
    }

    public void deductPoint(int amount) {
        if (this.point < amount) {
            throw new IllegalArgumentException("포인트가 부족합니다.");
        }
        this.point -= amount;
    }

    // 💡 사업자 정보를 등록하는 연관관계 편의 메서드
    public void registerBusinessInfo(BusinessInfo businessInfo) {
        this.businessInfo = businessInfo;
    }
}
