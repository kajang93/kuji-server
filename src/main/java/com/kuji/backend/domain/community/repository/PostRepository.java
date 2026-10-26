package com.kuji.backend.domain.community.repository;

import com.kuji.backend.domain.community.entity.Post;
import com.kuji.backend.domain.community.enums.PostCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    // 삭제와 댓글·반응 등록이 같은 게시글 잠금을 사용한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findByIdWithLock(@Param("id") Long id);
    
    /**
     * 카테고리별 게시글 목록 조회 (페이징 지원)
     */
    Page<Post> findAllByCategoryOrderByCreatedAtDesc(PostCategory category, Pageable pageable);
    
    /**
     * 전체 게시글 목록 최신순 조회
     */
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
