-- 직접 식별정보 없이 단계별 일자 집계와 짧은 중복 접수 번호만 보관한다.
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='15s';
CREATE TABLE IF NOT EXISTS signup_funnel_receipt (
    request_id UUID PRIMARY KEY,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT current_timestamp
);
CREATE INDEX IF NOT EXISTS idx_signup_funnel_receipt_received ON signup_funnel_receipt(received_at);
CREATE TABLE IF NOT EXISTS signup_funnel_daily (
    metric_date DATE NOT NULL,
    event_name VARCHAR(32) NOT NULL CHECK(event_name IN ('LANDING_VIEW','VIEW_ITEM_LIST','VIEW_ITEM','AUTH_VIEW','SIGNUP_VIEW','SIGNUP_START','SMS_SEND_ACCEPTED','PHONE_VERIFIED','SIGNUP_SUBMITTED','SIGNUP_COMPLETED','LOGIN_COMPLETED','AUTH_RETURNED_ITEM','VALIDATION_FAILED')),
    source VARCHAR(12) NOT NULL CHECK(source IN ('direct','threads','kakao','naver','google','other')),
    audience VARCHAR(12) NOT NULL CHECK(audience IN ('customer','business')),
    method VARCHAR(12) NOT NULL CHECK(method IN ('none','email','kakao','naver','google')),
    device VARCHAR(12) NOT NULL CHECK(device IN ('mobile','desktop')),
    event_count BIGINT NOT NULL CHECK(event_count>0),
    PRIMARY KEY(metric_date,event_name,source,audience,method,device)
);
COMMIT;
