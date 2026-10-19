# SwingMate Backend

SwingMate 앱의 Spring Boot 서버. 요구사항은 `swingMate_front/docs/backend/auth-member-requirements.md`를 따른다.

## 실행

```bash
docker compose up -d                 # PostgreSQL
KAKAO_APP_ID=1610065 mvn spring-boot:run      # 실제 카카오 로그인
OAUTH_MOCK_ENABLED=true mvn spring-boot:run   # 카카오·Google 없이 임의 토큰으로 로그인(개발 전용)
```

Swagger: http://localhost:8080/swagger-ui.html

`OAUTH_MOCK_ENABLED=true`면 카카오/Google을 호출하지 않고 `idToken` 문자열을 그대로 사용자 ID로 쓴다. 로컬 개발 전용이다.

## 환경 변수

| 이름 | 설명 |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL 접속 정보 (기본값은 `compose.yml`과 같음) |
| `JWT_SECRET` | JWT 서명 키, 32바이트 이상. 운영에서는 반드시 설정 |
| `KAKAO_APP_ID` | 카카오 앱 ID(숫자). 다른 앱의 토큰을 거부하는 데 쓴다 |
| `GOOGLE_CLIENT_IDS` | 앱의 Google OAuth 클라이언트 ID 목록(쉼표 구분) |

## 구현된 API (`/api/v1`)

| 메서드/경로 | 인증 | 설명 |
| --- | --- | --- |
| `POST /auth/social/login` | 없음 | 카카오(Access Token)·Google(ID Token) 로그인. 가입 완료면 액세스·리프레시 토큰, 아니면 온보딩 토큰 |
| `POST /auth/token/refresh` | 없음 | 리프레시 토큰 회전. 사용한 토큰은 재사용 불가 |
| `POST /auth/logout` | 없음 | 리프레시 토큰 폐기 |
| `GET /users/me` | 액세스 또는 온보딩 토큰 | 사용자·역할·온보딩 상태 |
| `PATCH /onboarding/role` | 온보딩 토큰 | `MEMBER`/`PRO` 역할 선택 |
| `POST /phone-verifications` | 온보딩 토큰 | SMS 인증번호 발송·재발송(`previousVerificationId`). 1분 1회, 하루 5회 |
| `POST /phone-verifications/{id}/confirm` | 온보딩 토큰 | 인증번호 확인. 3분 만료, 5회 실패 시 잠김. 성공하면 `verificationToken` |
| `PUT /onboarding/member` | 온보딩 토큰 + `verificationToken` | 일반 회원 가입 완료, 로그인 토큰 발급 |
| `PUT /onboarding/pro` | 온보딩 토큰 + `verificationToken` | 프로 가입 완료, 로그인 토큰 발급 |
| `POST /members/me/connection-code` | 액세스 토큰(회원) | 일회용 연결 코드 발급(5분, 1회용). 새로 받으면 이전 코드는 무효 |
| `POST /pros/me/connections` | 액세스 토큰(프로) | 회원 코드 입력 → 연결 요청(대기). 승인 전에는 회원 이름만 보임 |
| `GET /members/me/connections` | 액세스 토큰(회원) | 받은 요청(대기)·연결된 프로(연결 중), 프로 소개·경력 포함 |
| `POST /members/me/connections/{id}/approve`, `/reject` | 액세스 토큰(회원) | 연결 요청 승인(연결 중) 또는 거절(종료) |
| `GET /pros/me/connections` | 액세스 토큰(프로) | 보낸 요청(대기)·연결된 회원(연결 중) |

## 명세서 미확정 항목에 대해 임시로 정한 기본값

- **SMS 발송**: 사업자 미정이라 실제 문자를 보내지 않고 `SMS_FAKE_CODE`(기본 `123456`)만 통과한다.
- **휴대폰 번호 중복**: 한 번호는 한 계정만 쓸 수 있다(`PHONE_ALREADY_IN_USE`).
- **회원 연결 코드**: 헷갈리는 0/O, 1/I를 뺀 영문 대문자·숫자 6자리, 5분 유효, 한 번만 사용. 소문자로 입력해도 된다. 앱은 같은 값을 QR로 보여준다.
- **프로 활동**: 가입 즉시 활동 가능(관리자 승인 없음).
- **회원-프로 연결**: 기능 명세서 CONNECT-01 방식(회원 코드 표시 → 프로 입력 → 회원 승인). 한 회원이 여러 프로와 연결할 수 있다(MVP 범위의 "복수 프로 연결"). 거절된 프로도 새 코드로 다시 요청할 수 있다.
- **가입 완료 재요청**: 같은 역할로 다시 요청하면 데이터는 그대로 두고 토큰만 새로 발급한다.

## 테스트

```bash
mvn test
```

H2(PostgreSQL 모드)와 Mock 소셜 로그인으로 로그인·역할 선택·토큰 회전·로그아웃 흐름을 검증한다.
