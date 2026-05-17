package com.kuji.backend.domain.community.entity;

import com.kuji.backend.domain.community.enums.PostCategory;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post")
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 20) // 💡 DB 컬럼명인 post_type에 매핑!
    private PostCategory category;

    @Column(name = "view_count", nullable = false)
    @ColumnDefault("0")
    private int viewCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(length = 512)
    private String image1;

    @Column(length = 512)
    private String image2;

    @Column(length = 512)
    private String image3;

    @Builder
    public Post(String title, String content, PostCategory category, Member member, String image1, String image2, String image3) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.member = member;
        this.viewCount = 0;
        this.image1 = image1;
        this.image2 = image2;
        this.image3 = image3;
    }

    /**
     * 조회수 증가
     */
    public void increaseViewCount() {
        this.viewCount++;
    }

    /**
     * 게시글 수정
     */
    public void update(String title, String content, PostCategory category, String image1, String image2, String image3) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.image1 = image1;
        this.image2 = image2;
        this.image3 = image3;
    }
}
