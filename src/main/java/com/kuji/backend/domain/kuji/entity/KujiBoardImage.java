package com.kuji.backend.domain.kuji.entity;

import com.kuji.backend.domain.kuji.enums.BoardImageType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "kuji_board_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KujiBoardImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kuji_board_id", nullable = false)
    private KujiBoard kujiBoard;

    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    @Column(name = "\"sequence\"", nullable = false)
    private Integer sequence;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "image_type", nullable = false)
    private BoardImageType imageType;

    @Builder
    public KujiBoardImage(KujiBoard kujiBoard, String imageUrl, Integer sequence, BoardImageType imageType) {
        this.kujiBoard = kujiBoard;
        this.imageUrl = imageUrl;
        this.sequence = sequence;
        this.imageType = imageType;
    }
}
