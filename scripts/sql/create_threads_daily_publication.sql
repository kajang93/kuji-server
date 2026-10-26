-- 쓰레드의 계정별 하루 발행 기록과 재시도 상태를 보관합니다.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15s';
CREATE TABLE IF NOT EXISTS threads_daily_publication (
    account_id VARCHAR(64) NOT NULL,
    publish_date DATE NOT NULL,
    topic_key VARCHAR(64) NOT NULL,
    post_text TEXT NOT NULL,
    reply_text TEXT NOT NULL,
    post_id VARCHAR(64),
    reply_id VARCHAR(64),
    state VARCHAR(32) NOT NULL CHECK (state IN ('BODY_READY','BODY_PREPARING','BODY_PUBLISHING','BODY_PUBLISHED','REPLY_PREPARING','REPLY_PUBLISHING','COMPLETE','UNCERTAIN')),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, publish_date)
);
COMMIT;
