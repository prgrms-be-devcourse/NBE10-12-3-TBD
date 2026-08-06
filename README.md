# WhatToEat (뭐먹지?)

백엔드 10기 12회차 3차 프로젝트 1팀

맛집을 기록하고 공유하는 소셜 음식 플랫폼입니다. 카카오 지도 API 기반 맛집 검색, 피드 작성, 팔로우, 좋아요, 댓글, 식당 리스트 큐레이션 기능을 제공합니다.

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Kotlin 2.2.20 (JDK 24) |
| Framework | Spring Boot 4.1.0 |
| Database | MySQL 8 |
| Cache / Messaging | Redis (Lettuce) |
| ORM | Spring Data JPA (Hibernate) |
| Auth | JWT, OAuth2 (Kakao) |
| External API | Kakao Place API |
| API Docs | SpringDoc OpenAPI (Swagger) |
| CI/CD | GitHub Actions → AWS EC2 |

---

## 주요 기능

- **인증**: 자체 회원가입/로그인, 카카오 소셜 로그인 (JWT 쿠키 방식)
- **맛집**: 카카오 지도 API 연동 식당 검색 및 저장, 카테고리·지역 기반 추천
- **피드**: 맛집 방문 후기 작성·수정·삭제, 이미지 업로드
- **소셜**: 팔로우/언팔로우, 팔로잉 피드, 추천 피드
- **반응**: 피드 좋아요, 댓글 작성·삭제
- **식당 리스트**: 개인 맛집 컬렉션 생성·관리·공유·복사
- **알림**: 팔로워에게 새 피드 작성 알림 (Redis Stream 기반)

---

## 시스템 아키텍처

```
클라이언트
    │
    ▼
Spring Boot (EC2 :8081)
    ├── SecurityConfig (JWT Filter, OAuth2)
    ├── domain/
    │   ├── auth           - 로그인, 회원가입, 토큰 재발급
    │   ├── user           - 프로필 조회·수정
    │   ├── restaurant     - 식당 검색·저장
    │   ├── recommendation - 분위기 기반 식당 추천
    │   ├── feed           - 피드 CRUD + 팔로잉/추천 피드
    │   ├── comment        - 댓글 CRUD
    │   ├── feedlike       - 좋아요
    │   ├── follow         - 팔로우
    │   ├── notification   - 알림
    │   └── restaurantlist - 맛집 리스트 (내 리스트 + 저장한 리스트)
    │
    ├── MySQL (localhost:3306)
    └── Redis (localhost:6380)
            └── Stream: notification:feed-created
                    └── 피드 생성 → 팔로워 알림 DB 저장
```

### 알림 흐름

```
피드 작성
  → ApplicationEvent (AFTER_COMMIT)
  → Redis Stream 발행
  → FeedCreatedStreamListener
  → NotificationService.createFeedNotifications()
  → 팔로워 전원 Notification DB 저장
```

---

## API 명세

Swagger UI: `http://{EC2_HOST}:8081/swagger-ui/index.html` (배포 서버 주소는 팀 채널 참고)

### 인증 `/api/v1/auth`

| Method | Path | 설명 |
|---|---|---|
| POST | `/signup` | 회원가입 |
| POST | `/login` | 로그인 |
| POST | `/oauth/exchange` | 카카오 OAuth 코드 교환 |
| POST | `/reissue` | 액세스 토큰 재발급 |
| POST | `/logout` | 로그아웃 |

### 유저 `/api/v1/users`

| Method | Path | 설명 |
|---|---|---|
| GET | `/me` | 내 프로필 조회 |
| GET | `/{id}` | 유저 프로필 조회 |
| PATCH | `/me` | 프로필 수정 |
| PATCH | `/me/image` | 프로필 이미지 수정 |

### 식당 `/api/v1/restaurants`

| Method | Path | 설명 |
|---|---|---|
| GET | `/` | 식당 목록 조회 |
| POST | `/` | 식당 저장 (카카오 API) |
| GET | `/{id}` | 식당 상세 조회 |
| GET | `/today-hot` | 오늘의 인기 식당 |
| POST | `/recommend` | 분위기 기반 식당 추천 |

### 피드 `/api/v1/feeds`

| Method | Path | 설명 |
|---|---|---|
| POST | `/` | 피드 작성 (multipart) |
| GET | `/` | 피드 목록 조회 (userId·restaurantId 필터, 페이징) |
| GET | `/following` | 팔로잉 피드 목록 |
| GET | `/recommend` | 추천 피드 목록 |
| PUT | `/{id}` | 피드 수정 (multipart) |
| DELETE | `/{id}` | 피드 삭제 |

### 댓글 `/api/v1/feeds/{feedId}/comments`

| Method | Path | 설명 |
|---|---|---|
| GET | `/` | 댓글 목록 |
| POST | `/` | 댓글 작성 |
| DELETE | `/{commentId}` | 댓글 삭제 |

### 좋아요 `/api/v1/feeds/{feedId}/like`

| Method | Path | 설명 |
|---|---|---|
| POST | `/` | 좋아요 |
| DELETE | `/` | 좋아요 취소 |
| GET | `/` | 좋아요 여부 조회 |

### 팔로우 `/api/v1/follows`

| Method | Path | 설명 |
|---|---|---|
| POST | `/{followingId}` | 팔로우 |
| DELETE | `/{followingId}` | 언팔로우 |
| GET | `/followings` | 내 팔로잉 목록 |
| GET | `/followers` | 내 팔로워 목록 |
| GET | `/users/{userId}/followings` | 특정 유저의 팔로잉 목록 |
| GET | `/users/{userId}/followers` | 특정 유저의 팔로워 목록 |
| GET | `/users/{userId}/count` | 팔로워·팔로잉 수 |

### 식당 리스트 `/api/v1/lists`

| Method | Path | 설명 |
|---|---|---|
| POST | `/` | 리스트 생성 |
| GET | `/` | 내 리스트 목록 |
| GET | `/{id}` | 리스트 상세 |
| PUT | `/{id}` | 리스트 수정 |
| DELETE | `/{id}` | 리스트 삭제 |
| POST | `/{id}/items` | 식당 추가 |
| PUT | `/{id}/items/{itemId}` | 항목 수정 |
| DELETE | `/{id}/items/{itemId}` | 항목 삭제 |
| GET | `/popular` | 인기 리스트 |
| GET | `/all` | 전체 공개 리스트 |
| GET | `/all/{id}` | 공개 리스트 상세 |
| GET | `/others` | 다른 유저 리스트 |
| GET | `/users/{userId}` | 특정 유저의 공개 리스트 |
| POST | `/{id}/copy` | 리스트 복사 |

### 저장한 식당 리스트 `/api/v1/restaurant_lists`

| Method | Path | 설명 |
|---|---|---|
| POST | `/{id}/save` | 리스트 저장 (북마크) |
| DELETE | `/{id}/save` | 저장 취소 |
| GET | `/saved` | 내가 저장한 리스트 목록 |
| GET | `/{id}/saved` | 리스트 저장 여부 조회 |

### 알림 `/api/v1/notifications`

| Method | Path | 설명 |
|---|---|---|
| GET | `/` | 알림 목록 조회 (페이징) |
| GET | `/unread-count` | 읽지 않은 알림 개수 |
| PUT | `/{id}/read` | 알림 읽음 처리 |

---

## 로컬 실행 방법

### 사전 요구사항

- Java 25
- MySQL 8
- Redis (포트 6380)
- Kakao Developers 앱 (OAuth2, Place API)

### 1. MySQL / Redis 기동

```bash
docker compose up -d
```

`docker-compose.yaml`이 MySQL(3306), Redis(6380) 컨테이너를 띄웁니다.

### 2. 환경 설정 파일 생성

```bash
cp Backend/src/main/resources/application.yaml.example Backend/application.yaml
```

`application.yaml` 필수 값 설정:

```yaml
jwt:
  secret: {랜덤 Base64 문자열}

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/whattoeat?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: {DB 유저}
    password: {DB 비밀번호}
  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: {카카오 앱 키}
            client-secret: {카카오 앱 시크릿}
  data:
    redis:
      host: localhost
      port: 6380
```

### 3. 빌드 및 실행

```bash
cd Backend
./gradlew bootRun
```

기본 포트는 8080이며, Swagger UI는 `http://localhost:8080/swagger-ui/index.html`에서 확인할 수 있습니다.

---

## 배포 구조 (CI/CD)

`main` 브랜치에 push 시 GitHub Actions가 자동으로 배포합니다.

```
push to main
  → GitHub Actions
  → application.yaml 생성 (Secrets 주입)
  → ./gradlew bootJar
  → SCP로 EC2에 jar + yaml 전송
  → EC2에서 기존 프로세스 종료 후 재시작
  → 헬스체크 통과 시 완료
```

### 필요한 GitHub Secrets

| Secret | 설명 |
|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP |
| `EC2_USERNAME` | EC2 SSH 유저명 |
| `EC2_KEY` | EC2 PEM 키 내용 |
| `JWT_SECRET` | JWT 서명 키 |
| `FRONTEND_URL` | 프론트엔드 URL (CORS) |
| `KAKAO_CLIENT_ID` | 카카오 앱 키 |
| `KAKAO_CLIENT_SECRET` | 카카오 앱 시크릿 |

---

## DB 인덱스 구성

| 테이블 | 인덱스 | 용도 |
|---|---|---|
| feeds | idx_feed_user_id | 유저별 피드 조회 |
| feeds | idx_feed_restaurant_id | 식당별 피드 조회 |
| feed_like | idx_feed_like_feed_id | 피드 좋아요 수 집계 |
| feed_like | uk_feed_like_feed_user | 중복 좋아요 방지 |
| notifications | idx_notification_receiver_id | 수신자별 알림 조회 |
