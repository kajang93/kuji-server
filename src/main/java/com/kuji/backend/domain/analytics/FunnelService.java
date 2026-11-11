// 직접 식별자 없이 일자별 가입 단계 건수를 집계하고 중복 접수 번호를 짧게 보관한다.
package com.kuji.backend.domain.analytics;

import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class FunnelService {
    public enum Event { LANDING_VIEW, VIEW_ITEM_LIST, VIEW_ITEM, AUTH_VIEW, SIGNUP_VIEW, SIGNUP_START, SMS_SEND_ACCEPTED, PHONE_VERIFIED, SIGNUP_SUBMITTED, SIGNUP_COMPLETED, LOGIN_COMPLETED, AUTH_RETURNED_ITEM, VALIDATION_FAILED }
    public enum Source { direct, threads, kakao, naver, google, other }
    public enum Audience { customer, business }
    public enum Method { none, email, kakao, naver, google }
    public enum Device { mobile, desktop }
    public record Input(@NotNull UUID requestId, @NotNull Event event, @NotNull Source source, @NotNull Audience audience, @NotNull Method method, @NotNull Device device) {}
    public record Count(Event event, long count) {}
    public record SourceCount(Source source, Event event, long count) {}
    public record Report(int days, List<Count> counts, List<SourceCount> sources, long confirmedSignups, String notice) {}
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final int maxEvents;
    private long minute = -1;
    private int requests;
    public FunnelService(JdbcTemplate jdbc, @Value("${analytics.max-events-per-minute:600}") int maxEvents) {
        this.jdbc = jdbc; this.maxEvents = Math.max(1, maxEvents);
    }
    private synchronized boolean allow() {
        long now = System.currentTimeMillis() / 60000;
        if (now != minute) { minute = now; requests = 0; }
        return ++requests <= maxEvents;
    }
    @Transactional
    public void record(Input input) {
        if (input == null || input.requestId() == null || input.event() == null || input.source() == null || input.audience() == null || input.method() == null || input.device() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "집계 항목을 확인해 주세요.");
        }
        if (!allow()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 수집합니다.");
        jdbc.update("""
            WITH accepted AS (
                INSERT INTO signup_funnel_receipt(request_id,received_at) VALUES (?,current_timestamp)
                ON CONFLICT DO NOTHING RETURNING request_id
            )
            INSERT INTO signup_funnel_daily(metric_date,event_name,source,audience,method,device,event_count)
            SELECT ?,?,?,?,?,?,1 FROM accepted
            ON CONFLICT (metric_date,event_name,source,audience,method,device)
            DO UPDATE SET event_count=signup_funnel_daily.event_count+1
            """, input.requestId(), Date.valueOf(LocalDate.now(KOREA)), input.event().name(), input.source().name(), input.audience().name(), input.method().name(), input.device().name());
    }
    @Transactional(readOnly = true)
    public Report report(int days) {
        if (days < 1 || days > 90) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "집계 기간은 1~90일입니다.");
        LocalDate since = LocalDate.now(KOREA).minusDays(days - 1L);
        List<Count> counts = jdbc.query("SELECT event_name,sum(event_count) FROM signup_funnel_daily WHERE metric_date>=? GROUP BY event_name ORDER BY event_name", (rs,n) -> new Count(Event.valueOf(rs.getString(1)),rs.getLong(2)), Date.valueOf(since));
        List<SourceCount> sources = jdbc.query("SELECT source,event_name,sum(event_count) FROM signup_funnel_daily WHERE metric_date>=? GROUP BY source,event_name ORDER BY source,event_name", (rs,n) -> new SourceCount(Source.valueOf(rs.getString(1)),Event.valueOf(rs.getString(2)),rs.getLong(3)), Date.valueOf(since));
        Long members = jdbc.queryForObject("SELECT count(*) FROM member WHERE created_at>=? AND role IN ('USER','BIZ')", Long.class, since.atStartOfDay());
        return new Report(days, counts, sources, members == null ? 0 : members,
                "단계 건수는 브라우저에서 보고하며 동일 브라우저 세션·일자·항목 중복을 줄인 참고 집계입니다. 순방문자·고객별 연결 전환율이 아니고, 수집 차단·재방문·오류에 따라 차이가 있습니다. 회원 원장의 신규 가입 확정수는 별도로 표시하며 유입별 가입 완료로 추정하지 않습니다. 이름·이메일·전화·회원번호·IP·전체 URL을 저장하지 않습니다.");
    }
    @Scheduled(cron = "0 20 * * * *", zone = "Asia/Seoul")
    @Transactional
    public void expire() {
        jdbc.update("DELETE FROM signup_funnel_receipt WHERE received_at < current_timestamp - interval '48 hours'");
        jdbc.update("DELETE FROM signup_funnel_daily WHERE metric_date < ?", Date.valueOf(LocalDate.now(KOREA).minusDays(89)));
    }
}
