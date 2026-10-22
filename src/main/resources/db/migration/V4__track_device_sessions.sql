ALTER TABLE refresh_tokens ADD COLUMN token_id UUID;
ALTER TABLE refresh_tokens ADD COLUMN device_name VARCHAR(120);
ALTER TABLE refresh_tokens ADD COLUMN platform VARCHAR(20);
ALTER TABLE refresh_tokens ADD COLUMN app_version VARCHAR(40);
ALTER TABLE refresh_tokens ADD COLUMN device_id UUID;
ALTER TABLE refresh_tokens ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE refresh_tokens ADD COLUMN last_used_at TIMESTAMP WITH TIME ZONE;

UPDATE refresh_tokens
SET token_id = id,
    device_id = id,
    device_name = '기존 로그인 기기',
    platform = 'UNKNOWN',
    created_at = CURRENT_TIMESTAMP,
    last_used_at = CURRENT_TIMESTAMP;

ALTER TABLE refresh_tokens ALTER COLUMN token_id SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN device_id SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN device_name SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN platform SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN last_used_at SET NOT NULL;
ALTER TABLE refresh_tokens ADD CONSTRAINT uk_refresh_tokens_token_id UNIQUE (token_id);
ALTER TABLE refresh_tokens ADD CONSTRAINT uk_refresh_tokens_user_device UNIQUE (user_id, device_id);
