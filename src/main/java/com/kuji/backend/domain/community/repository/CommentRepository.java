package com.kuji.backend.domain.community.repository;

import com.kuji.backend.domain.community.entity.Comment;
import com.kuji.backend.domain.community.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findAllByPostOrderByCreatedAtAsc(Post post);
    long countByPost(Post post);
}
