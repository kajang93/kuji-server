package com.kuji.backend.domain.kuji.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "kuji_item_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KujiItemImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kuji_item_id", nullable = false)
    private KujiItem kujiItem;

    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    @Column(nullable = false)
    private Integer sequence;

    @Builder
    public KujiItemImage(KujiItem kujiItem, String imageUrl, Integer sequence) {
        this.kujiItem = kujiItem;
        this.imageUrl = imageUrl;
        this.sequence = sequence;
    }
}
