package com.kuji.backend.domain.community.entity;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "post_like",
    uniqueConstraints = {
        @UniqueConstraint(name = "uc_post_like", columnNames = {"post_id", "member_id"})
    }
)
public class PostLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Builder
    public PostLike(Post post, Member member) {
        this.post = post;
        this.member = member;
    }
}
