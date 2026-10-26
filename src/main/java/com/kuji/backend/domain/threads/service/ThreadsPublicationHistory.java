// 날짜별 쓰레드 발행 상태와 본문을 저장해 재시도와 재배포 때 중복 게시를 막습니다.
package com.kuji.backend.domain.threads.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ThreadsPublicationHistory {
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<Publication> find(String accountId, LocalDate day) {
        return jdbcTemplate.query("select * from threads_daily_publication where account_id=? and publish_date=?",
                (rs, n) -> new Publication(rs.getString("account_id"), rs.getObject("publish_date", LocalDate.class),
                        rs.getString("topic_key"), rs.getString("post_text"), rs.getString("reply_text"),
                        rs.getString("post_id"), rs.getString("reply_id"), rs.getString("state")), accountId, day)
                .stream().findFirst();
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Publication> recent(String accountId, LocalDate since) {
        return jdbcTemplate.query("select * from threads_daily_publication where account_id=? and publish_date>=? order by publish_date desc",
                (rs, n) -> new Publication(rs.getString("account_id"), rs.getObject("publish_date", LocalDate.class),
                        rs.getString("topic_key"), rs.getString("post_text"), rs.getString("reply_text"),
                        rs.getString("post_id"), rs.getString("reply_id"), rs.getString("state")), accountId, since);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String accountId, LocalDate day, String topicKey, String post, String reply) {
        return jdbcTemplate.update("insert into threads_daily_publication(account_id,publish_date,topic_key,post_text,reply_text,state) " +
                        "values (?,?,?,?,?,'BODY_PREPARING') on conflict (account_id,publish_date) do nothing",
                accountId, day, topicKey, post, reply) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean transition(String accountId, LocalDate day, String expected, String next) {
        return jdbcTemplate.update("update threads_daily_publication set state=?, updated_at=current_timestamp " +
                "where account_id=? and publish_date=? and state=?", next, accountId, day, expected) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void bodyPublished(String accountId, LocalDate day, String postId) {
        if (jdbcTemplate.update("update threads_daily_publication set post_id=?, state='BODY_PUBLISHED', updated_at=current_timestamp " +
                "where account_id=? and publish_date=? and state='BODY_PUBLISHING'", postId, accountId, day) != 1) {
            throw new IllegalStateException("쓰레드 본문 발행 결과를 저장하지 못했습니다. 재게시 전에 발행 상태를 확인해주세요.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replyPublished(String accountId, LocalDate day, String replyId) {
        if (jdbcTemplate.update("update threads_daily_publication set reply_id=?, state='COMPLETE', updated_at=current_timestamp " +
                "where account_id=? and publish_date=? and state='REPLY_PUBLISHING'", replyId, accountId, day) != 1) {
            throw new IllegalStateException("쓰레드 첫 댓글 발행 결과를 저장하지 못했습니다. 추가 발행 전에 상태를 확인해주세요.");
        }
    }

    public record Publication(String accountId, LocalDate day, String topicKey, String postText, String replyText,
                              String postId, String replyId, String state) {
    }
}
