package com.kuji.backend.domain.community.repository;

import com.kuji.backend.domain.community.entity.Comment;
import com.kuji.backend.domain.community.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Modifying(flushAutomatically = true)
    @Query("delete from Comment entry where entry.post = :post")
    void deleteAllByPost(@Param("post") Post post);
    List<Comment> findAllByPostOrderByCreatedAtAsc(Post post);
    long countByPost(Post post);
}
