package com.kuji.backend.domain.community.service;

import com.kuji.backend.domain.community.dto.PostCreateRequest;
import com.kuji.backend.domain.community.dto.PostResponse;
import com.kuji.backend.domain.community.entity.Post;
import com.kuji.backend.domain.community.enums.PostCategory;
import com.kuji.backend.domain.community.repository.PostRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.global.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final S3Service s3Service;

    /**
     * 게시글 작성
     */
    @Transactional
    public Long createPost(Long memberId, PostCreateRequest request, List<MultipartFile> files) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        // 최대 3개까지 사진 업로드 및 URL 추출
        List<String> imageUrls = uploadFiles(files);
        String img1 = imageUrls.size() > 0 ? imageUrls.get(0) : null;
        String img2 = imageUrls.size() > 1 ? imageUrls.get(1) : null;
        String img3 = imageUrls.size() > 2 ? imageUrls.get(2) : null;

        Post post = Post.builder()
                .title(request.title())
                .content(request.content())
                .category(request.category())
                .member(member)
                .image1(img1)
                .image2(img2)
                .image3(img3)
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
    public void updatePost(Long memberId, Long postId, PostCreateRequest request, List<MultipartFile> files) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if (!post.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 게시글만 수정할 수 있습니다.");
        }

        // 새 파일이 있으면 업로드, 없으면 기존 이미지 유지
        String img1 = post.getImage1();
        String img2 = post.getImage2();
        String img3 = post.getImage3();

        if (files != null && !files.isEmpty()) {
            List<String> imageUrls = uploadFiles(files);
            img1 = imageUrls.size() > 0 ? imageUrls.get(0) : null;
            img2 = imageUrls.size() > 1 ? imageUrls.get(1) : null;
            img3 = imageUrls.size() > 2 ? imageUrls.get(2) : null;
        }

        post.update(request.title(), request.content(), request.category(), img1, img2, img3);
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

        // S3에서 이미지 삭제
        if (post.getImage1() != null) s3Service.deleteFile(post.getImage1());
        if (post.getImage2() != null) s3Service.deleteFile(post.getImage2());
        if (post.getImage3() != null) s3Service.deleteFile(post.getImage3());

        postRepository.delete(post);
    }

    private List<String> uploadFiles(List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        if (files != null) {
            // 최대 3개까지만 제한
            int count = Math.min(files.size(), 3);
            for (int i = 0; i < count; i++) {
                String url = s3Service.uploadFile("posts", files.get(i));
                if (url != null) urls.add(url);
            }
        }
        return urls;
    }
}
