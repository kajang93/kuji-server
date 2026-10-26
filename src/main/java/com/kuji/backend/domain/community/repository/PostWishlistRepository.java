package com.kuji.backend.domain.community.repository;

import com.kuji.backend.domain.community.entity.Post;
import com.kuji.backend.domain.community.entity.PostWishlist;
import com.kuji.backend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostWishlistRepository extends JpaRepository<PostWishlist, Long> {
    @Modifying(flushAutomatically = true)
    @Query("delete from PostWishlist entry where entry.post = :post")
    void deleteAllByPost(@Param("post") Post post);
    Optional<PostWishlist> findByPostAndMember(Post post, Member member);
    boolean existsByPostAndMember(Post post, Member member);
}
