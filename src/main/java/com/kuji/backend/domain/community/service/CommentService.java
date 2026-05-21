package com.kuji.backend.domain.community.service;

import com.kuji.backend.domain.community.dto.CommentRequest;
import com.kuji.backend.domain.community.dto.CommentResponse;
import com.kuji.backend.domain.community.entity.Comment;
import com.kuji.backend.domain.community.entity.Post;
import com.kuji.backend.domain.community.repository.CommentRepository;
import com.kuji.backend.domain.community.repository.PostRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.notification.service.NotificationService;
import com.kuji.backend.domain.notification.entity.NotificationType;
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
    private final NotificationService notificationService;

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

        // 본인 게시글이 아닌 경우에만 푸시 알림 발송
        if (!post.getMember().getId().equals(memberId)) {
            String title = "새로운 댓글이 달렸습니다!";
            String body = member.getNickname() + "님이 댓글을 남겼습니다: " + comment.getContent();
            notificationService.sendNotification(
                    post.getMember(),
                    title,
                    body,
                    NotificationType.COMMENT,
                    String.valueOf(post.getId())
            );
        }
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
