package com.kuji.backend.domain.community.service;

import com.kuji.backend.domain.community.dto.PostCreateRequest;
import com.kuji.backend.domain.community.dto.PostResponse;
import com.kuji.backend.domain.community.entity.Post;
import com.kuji.backend.domain.community.enums.PostCategory;
import com.kuji.backend.domain.community.repository.PostRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    /**
     * 게시글 작성
     */
    @Transactional
    public Long createPost(Long memberId, PostCreateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        Post post = Post.builder()
                .title(request.title())
                .content(request.content())
                .category(request.category())
                .member(member)
                .build();

        return postRepository.save(post).getId();
    }

    /**
     * 게시글 상세 조회 (조회수 증가 포함)
     */
    @Transactional
    public PostResponse getPostDetail(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        
        post.increaseViewCount();
        return PostResponse.from(post);
    }

    /**
     * 게시글 목록 조회
     */
    public Page<PostResponse> getPosts(PostCategory category, Pageable pageable) {
        Page<Post> posts;
        if (category == null) {
            posts = postRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            posts = postRepository.findAllByCategoryOrderByCreatedAtDesc(category, pageable);
        }
        
        return posts.map(PostResponse::from);
    }

    /**
     * 게시글 수정
     */
    @Transactional
    public void updatePost(Long memberId, Long postId, PostCreateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 게시글만 수정할 수 있습니다.");
        }

        post.update(request.title(), request.content(), request.category());
    }

    /**
     * 게시글 삭제
     */
    @Transactional
    public void deletePost(Long memberId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 게시글만 삭제할 수 있습니다.");
        }

        postRepository.delete(post);
    }
}
