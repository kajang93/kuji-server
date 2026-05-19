package com.kuji.backend.domain.community.service;

import com.kuji.backend.domain.community.entity.Post;
import com.kuji.backend.domain.community.entity.PostLike;
import com.kuji.backend.domain.community.entity.PostWishlist;
import com.kuji.backend.domain.community.repository.PostLikeRepository;
import com.kuji.backend.domain.community.repository.PostRepository;
import com.kuji.backend.domain.community.repository.PostWishlistRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostInteractionService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostWishlistRepository postWishlistRepository;

    @Transactional
    public void toggleLike(Long memberId, Long postId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        Optional<PostLike> existingLike = postLikeRepository.findByPostAndMember(post, member);
        if (existingLike.isPresent()) {
            postLikeRepository.delete(existingLike.get());
        } else {
            postLikeRepository.save(PostLike.builder().post(post).member(member).build());
        }
    }

    @Transactional
    public void toggleWishlist(Long memberId, Long postId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        Optional<PostWishlist> existingWishlist = postWishlistRepository.findByPostAndMember(post, member);
        if (existingWishlist.isPresent()) {
            postWishlistRepository.delete(existingWishlist.get());
        } else {
            postWishlistRepository.save(PostWishlist.builder().post(post).member(member).build());
        }
    }
}
