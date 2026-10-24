-- 쓰레드 댓글 감시 시작 시각과 전달 내역을 보관합니다.
-- 댓글 감시를 활성화하기 전에 운영 데이터베이스에 한 번 적용합니다.
BEGIN;
CREATE TABLE IF NOT EXISTS threads_reply_monitor_state (
    account_id VARCHAR(64) PRIMARY KEY,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS threads_reply_notification (
    reply_id VARCHAR(64) PRIMARY KEY,
    notified_at TIMESTAMP WITH TIME ZONE NOT NULL
);
COMMIT;
