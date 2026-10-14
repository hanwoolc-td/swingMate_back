# SwingMate Backend

SwingMate 앱의 Spring Boot 서버. 요구사항은 `swingMate_front/docs/backend/auth-member-requirements.md`를 따른다.

## 실행

```bash
docker compose up -d                 # PostgreSQL
OAUTH_MOCK_ENABLED=true mvn spring-boot:run
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

아직 없음: SMS 인증, 회원·프로 온보딩 완료, 프로 코드 연결.

## 테스트

```bash
mvn test
```

H2(PostgreSQL 모드)와 Mock 소셜 로그인으로 로그인·역할 선택·토큰 회전·로그아웃 흐름을 검증한다.
