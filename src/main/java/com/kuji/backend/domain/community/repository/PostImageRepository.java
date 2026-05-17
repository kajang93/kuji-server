package com.kuji.backend.domain.community.repository;

import com.kuji.backend.domain.community.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    List<PostImage> findAllByPostIdOrderBySequenceAsc(Long postId);
    void deleteAllByPostId(Long postId);
}
