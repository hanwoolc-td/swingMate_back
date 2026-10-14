CREATE TABLE users (
    id                UUID PRIMARY KEY,
    role              VARCHAR(20),
    onboarding_status VARCHAR(20)  NOT NULL,
    name              VARCHAR(50),
    phone_number      VARCHAR(11),
    phone_verified_at TIMESTAMP WITH TIME ZONE,
    profile_image_url VARCHAR(500),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE social_accounts (
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users (id),
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email            VARCHAR(255),
    connected_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_social_accounts_provider_user UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_social_accounts_user_id ON social_accounts (user_id);

-- 리프레시 토큰은 원문 대신 토큰 ID(jti)만 저장한다. 행이 없으면 폐기된 토큰이다.
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
