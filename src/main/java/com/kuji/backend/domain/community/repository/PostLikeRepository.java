package com.kuji.backend.domain.community.repository;

import com.kuji.backend.domain.community.entity.Post;
import com.kuji.backend.domain.community.entity.PostLike;
import com.kuji.backend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByPostAndMember(Post post, Member member);
    boolean existsByPostAndMember(Post post, Member member);
    long countByPost(Post post);
}
