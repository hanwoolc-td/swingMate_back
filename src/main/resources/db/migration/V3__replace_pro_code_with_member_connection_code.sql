-- 기능 명세서 CONNECT-01: 회원이 일회용 코드를 보여주고, 프로가 입력하고, 회원이 승인한다.
-- 이전 방식(회원이 프로의 고정 코드를 입력해 즉시 연결)을 대체한다.
DROP TABLE member_pro_connections;
ALTER TABLE pro_profiles DROP COLUMN pro_code;

CREATE TABLE connection_codes (
    id         UUID PRIMARY KEY,
    member_id  UUID        NOT NULL REFERENCES users (id),
    code       VARCHAR(10) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_connection_codes_member_id ON connection_codes (member_id);

-- 프로-회원 한 쌍은 한 행만 둔다. 종료 후 다시 연결하면 같은 행이 대기 상태로 돌아간다.
CREATE TABLE pro_member_connections (
    id           UUID PRIMARY KEY,
    pro_id       UUID        NOT NULL REFERENCES users (id),
    member_id    UUID        NOT NULL REFERENCES users (id),
    status       VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_pro_member_connections_pair UNIQUE (pro_id, member_id)
);

CREATE INDEX idx_pro_member_connections_member_id ON pro_member_connections (member_id);
