package com.kuji.backend.domain.community.repository;

import com.kuji.backend.domain.community.entity.Post;
import com.kuji.backend.domain.community.enums.PostCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
    
    /**
     * 카테고리별 게시글 목록 조회 (페이징 지원)
     */
    Page<Post> findAllByCategoryOrderByCreatedAtDesc(PostCategory category, Pageable pageable);
    
    /**
     * 전체 게시글 목록 최신순 조회
     */
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
