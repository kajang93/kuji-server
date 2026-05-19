package com.kuji.backend.domain.community.service;

import com.kuji.backend.domain.community.dto.CommentRequest;
import com.kuji.backend.domain.community.dto.CommentResponse;
import com.kuji.backend.domain.community.entity.Comment;
import com.kuji.backend.domain.community.entity.Post;
import com.kuji.backend.domain.community.repository.CommentRepository;
import com.kuji.backend.domain.community.repository.PostRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void createComment(Long memberId, Long postId, CommentRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        Comment comment = Comment.builder()
                .content(request.content())
                .post(post)
                .member(member)
                .build();

        commentRepository.save(comment);
    }

    public List<CommentResponse> getCommentsByPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        return commentRepository.findAllByPostOrderByCreatedAtAsc(post).stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateComment(Long memberId, Long commentId, CommentRequest request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다."));

        if (!comment.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 댓글만 수정할 수 있습니다.");
        }

        comment.update(request.content());
    }

    @Transactional
    public void deleteComment(Long memberId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다."));

        if (!comment.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 댓글만 삭제할 수 있습니다.");
        }

        commentRepository.delete(comment);
    }
}
