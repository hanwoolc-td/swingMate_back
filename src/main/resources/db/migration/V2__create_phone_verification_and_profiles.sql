-- 한 휴대폰 번호는 한 계정만 쓴다. NULL(가입 미완료)은 여러 개 허용된다.
CREATE UNIQUE INDEX uk_users_phone_number ON users (phone_number);

CREATE TABLE phone_verifications (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id),
    phone_number VARCHAR(11) NOT NULL,
    purpose      VARCHAR(40) NOT NULL,
    code_hash    VARCHAR(64) NOT NULL,
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count INT        NOT NULL,
    resend_count INT         NOT NULL,
    verified_at  TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_phone_verifications_phone_created ON phone_verifications (phone_number, created_at);

CREATE TABLE member_profiles (
    user_id         UUID PRIMARY KEY REFERENCES users (id),
    golf_experience VARCHAR(30) NOT NULL
);

CREATE TABLE member_lesson_interests (
    user_id  UUID        NOT NULL REFERENCES member_profiles (user_id),
    interest VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, interest)
);

CREATE TABLE pro_profiles (
    user_id      UUID PRIMARY KEY REFERENCES users (id),
    introduction VARCHAR(200) NOT NULL,
    pro_code     VARCHAR(10)  NOT NULL UNIQUE
);

CREATE TABLE pro_careers (
    user_id    UUID         NOT NULL REFERENCES pro_profiles (user_id),
    sort_order INT          NOT NULL,
    career     VARCHAR(200) NOT NULL,
    PRIMARY KEY (user_id, sort_order)
);

CREATE TABLE pro_availability (
    user_id     UUID        NOT NULL REFERENCES pro_profiles (user_id),
    day_of_week VARCHAR(10) NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL
);

CREATE INDEX idx_pro_availability_user_id ON pro_availability (user_id);

-- MVP는 한 회원이 여러 프로와 연결될 수 있다.
CREATE TABLE member_pro_connections (
    member_id    UUID NOT NULL REFERENCES users (id),
    pro_id       UUID NOT NULL REFERENCES users (id),
    connected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (member_id, pro_id)
);
