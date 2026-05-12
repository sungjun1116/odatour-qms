# Odatour Waiting System Requirements

## 1. 목적

박람회 부스 방문자가 QR 코드를 통해 간단히 웨이팅을 등록하고, 관리자가 호출, 현장도착 확인, 입장완료, 노쇼 처리를 할 수 있는 웹 기반 웨이팅 시스템을 만든다.

실시간 앱, 별도 모바일 앱, 복잡한 대기열 인프라는 사용하지 않는다. 사용자는 웹 페이지의 새로고침 버튼으로 남은 순서를 확인하고, 관리자가 호출하면 카카오 알림톡을 받는다.

## 2. 기술 스택

### Backend

- Java 25
- Spring Boot 4.0.6
- Spring Web MVC
- Spring JDBC
- Thymeleaf
- Gradle

### Database

- 로컬: embedded H2
- 운영: PostgreSQL + Docker Compose

Redis는 초기 버전에서는 사용하지 않는다. 로컬 개발은 Docker 없이 H2로 실행하고, 운영 데이터는 유실되면 안 되므로 PostgreSQL을 기준 저장소로 사용한다.

### External Service

- SOLAPI 카카오 알림톡

카카오 알림톡은 관리자가 관리자 화면에서 대기자를 직접 호출할 때 발송한다. 승인된 템플릿 ID를 사용하며, 중복 발송 방지를 위해 발송 완료 시각을 DB에 저장한다.

## 3. 사용자 시나리오

1. 방문자가 부스 QR 코드를 스캔한다.
2. 모바일 웹 페이지에서 휴대폰 번호를 입력한다.
3. 개인정보 수집 및 이용 동의에 체크한다.
4. 웨이팅 리스트에 등록된다.
5. 등록 완료 화면에서 현재 남은 순서를 확인한다.
6. 사용자는 새로고침 버튼을 눌러 남은 순서를 다시 확인한다.
7. 관리자가 호출하면 카카오 알림톡을 받는다.
8. 사용자는 더 이상 대기하지 않을 경우 웨이팅을 취소할 수 있다.
9. 관리자가 현장도착 확인, 입장완료 또는 노쇼 처리를 한다.

## 4. 관리자 시나리오

1. 관리자는 관리자 페이지에 접속한다.
2. 기본 관리자 페이지에서 현재 처리해야 하는 웨이팅 목록을 등록 순서대로 확인한다.
3. 호출된 대기자에 대해 현장도착 확인 처리를 할 수 있다.
4. 현장도착한 대기자에 대해 입장완료 처리를 할 수 있다.
5. 호출 후 현장에 오지 않은 대기자에 대해 노쇼 처리를 할 수 있다.
6. 고객이 현장에서 직접 취소를 요청하면 관리자가 취소 처리할 수 있다.
7. 입장완료, 노쇼 또는 취소 처리된 사용자는 남은 순서 계산 대상에서 제외된다.
8. 관리자는 호출이 필요한 대기자에게 카카오 알림톡 호출 버튼을 눌러 발송한다.
9. 입장 완료된 사용자는 별도 화면에서 모아서 확인한다.

## 5. 기능 요구사항

### 5.1 웨이팅 등록

- 휴대폰 번호는 필수 입력값이다.
- 휴대폰 번호는 `01012345678` 또는 `010-1234-5678` 형식만 허용한다.
- 사용자 화면에서는 숫자 입력 시 `010-1234-5678` 형식으로 하이픈을 자동 삽입한다.
- 개인정보 수집 및 이용 동의는 필수다.
- 동일 휴대폰 번호의 중복 등록 정책은 운영 편의를 위해 다음 중 하나로 정한다.
  - 기본 정책: 진행 중인 웨이팅이 있으면 중복 등록을 막는다.
  - 진행 중 상태: `WAITING`, `CALLED`, `ARRIVED`
- 등록 시 대기 상태는 `WAITING`으로 저장한다.
- 등록 완료 후 사용자 조회 화면으로 이동한다.

### 5.2 남은 순서 조회

- 사용자는 등록 완료 화면에서 남은 순서를 확인할 수 있다.
- 남은 순서는 조회 시점에 DB 기준으로 계산한다.
- 계산 기준은 나보다 먼저 등록되었고 아직 처리되지 않은 사람 수다.
- 대상 상태는 `WAITING`, `CALLED`, `ARRIVED`다.
- 예상 대기시간은 남은 팀 수 기준으로 1팀당 3분으로 계산해 표시한다.
- 사용자는 새로고침 버튼으로 최신 순서를 다시 조회한다.

### 5.3 웨이팅 취소

- 사용자는 본인의 대기 상태 화면에서 웨이팅을 취소할 수 있다.
- 취소 가능 상태는 `WAITING`이다.
- 취소 시 상태를 `CANCELED`로 변경한다.
- 취소된 사용자는 남은 순서 계산 대상에서 제외한다.
- 취소된 사용자는 관리자 기본 웨이팅 목록에서 제외한다.
- 취소된 사용자는 관리자 기본 요약 지표에도 포함하지 않는다.
- 취소 완료 후 사용자 화면에는 취소 완료 상태를 표시한다.

### 5.4 관리자 취소 처리

- 관리자는 고객 요청을 받아 대기자를 `CANCELED` 상태로 변경할 수 있다.
- 관리자 취소 가능 상태는 `WAITING`이다.
- 취소된 사용자는 남은 순서 계산 대상에서 제외한다.
- 취소된 사용자는 관리자 기본 웨이팅 목록과 요약 지표에서 제외한다.

### 5.5 카카오 알림톡 발송

- 카카오 알림톡은 관리자가 관리자 화면에서 개별 호출 버튼 또는 부족 인원 호출 버튼을 눌렀을 때만 발송한다.
- 카카오 알림톡 발송에는 승인된 템플릿 ID를 사용한다.
- 카카오 알림톡은 `WAITING` 상태이면서 `notified_at`이 없는 사용자에게만 발송한다.
- 카카오 알림톡은 한 사용자에게 한 번만 발송한다.
- 중복 발송 방지를 위해 `notified_at`을 저장한다.
- 카카오 알림톡 발송 성공 후 상태를 `CALLED`로 변경한다.
- 카카오 알림톡 발송 실패 시 로그를 남기고, `notified_at`은 저장하지 않는다.
- 초기 버전에서는 별도 메시지 큐 없이 동일 애플리케이션 서비스 로직 안에서 처리한다.

### 5.6 관리자 현장도착 확인

- 관리자는 호출된 대기자를 `ARRIVED` 상태로 변경할 수 있다.
- `ARRIVED`는 고객이 실제로 부스 대기줄에 도착했고 운영자가 확인한 상태다.
- 현장도착한 사용자는 아직 처리 중인 웨이팅으로 보며 남은 순서 계산 대상에 포함한다.

### 5.7 관리자 입장완료 처리

- 관리자는 현장도착한 대기자를 `ENTERED` 상태로 변경할 수 있다.
- `ENTERED`는 고객이 실제로 VR 체험을 시작한 상태다.
- 입장완료된 사용자는 남은 순서 계산 대상에서 제외한다.

### 5.8 관리자 노쇼 처리

- 관리자는 호출된 대기자 또는 현장도착 처리된 대기자를 `NO_SHOWED` 상태로 변경할 수 있다.
- `NO_SHOWED`는 호출 후 고객이 현장에 오지 않았거나 운영 중 노쇼로 판단되어 운영자가 처리한 상태다.
- 노쇼 처리된 사용자는 남은 순서 계산 대상에서 제외한다.

## 6. 상태 모델

```text
WAITING -> CALLED -> ARRIVED -> ENTERED
WAITING -> CANCELED
CALLED  -> NO_SHOWED
ARRIVED -> NO_SHOWED
```

### 상태 정의

| 상태 | 설명 |
| --- | --- |
| `WAITING` | 웨이팅 등록 후 아직 호출되지 않은 상태 |
| `CALLED` | 고객에게 부스로 오라고 알림을 보낸 상태 |
| `ARRIVED` | 고객이 실제로 부스 대기줄에 도착했고 운영자가 확인한 상태 |
| `ENTERED` | 고객이 실제로 VR 체험을 시작한 상태 |
| `NO_SHOWED` | 호출 후 고객이 현장에 오지 않았거나 운영 중 노쇼로 판단되어 운영자가 처리한 상태 |
| `CANCELED` | 호출 전 고객 또는 관리자가 취소한 상태 |

## 7. 데이터 모델 초안

### waiting_entry

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `id` | bigint | PK |
| `phone_number` | varchar | 휴대폰 번호 |
| `consent_agreed` | boolean | 개인정보 동의 여부 |
| `status` | varchar | 웨이팅 상태 |
| `notified_at` | timestamp | 카카오 알림톡 발송 완료 시각 |
| `arrived_at` | timestamp | 현장도착 확인 시각 |
| `entered_at` | timestamp | 입장완료 시각 |
| `no_show_at` | timestamp | 노쇼 처리 시각 |
| `canceled_at` | timestamp | 사용자 취소 시각 |
| `created_at` | timestamp | 등록 시각 |
| `updated_at` | timestamp | 수정 시각 |

초기 구현에서는 별도의 대기번호 컬럼을 두지 않는다. 대기 순서는 `created_at`, `id` 기준으로 계산한다.

## 8. 화면 요구사항

### 사용자 화면

- 휴대폰 번호 입력
- 개인정보 수집 및 이용 동의 체크박스
- 웨이팅 등록 버튼
- 등록 완료 후 남은 순서 표시
- 등록 화면과 상태 화면의 예상 대기시간 표시
- 새로고침 버튼
- 웨이팅 취소 버튼
- 취소 완료 상태 표시

### 관리자 화면

- 현재 대기 목록
- 기본 화면 `/admin/waitings`에는 `WAITING`, `CALLED`, `ARRIVED` 상태만 표시
- 기본 화면 요약 지표에는 `WAITING`, `CALLED`, `ARRIVED` 상태만 포함
- `CANCELED` 상태는 기본 화면의 요약과 상세 목록에서 제외
- `ENTERED` 상태는 기본 화면의 요약과 상세 목록에서 제외
- `NO_SHOWED` 상태는 기본 화면의 요약과 상세 목록에서 제외
- 입장 완료된 사용자는 `/admin/waitings/entered` 화면에서 별도로 표시
- 기본 웨이팅 목록은 10명 초과 시 페이징 처리
- 입장 완료 목록은 10명 초과 시 페이징 처리
- 휴대폰 번호 표시
- 등록 시각
- 현재 상태
- 남은 순서
- 카카오 알림톡 호출 버튼
- 현장도착 확인 버튼
- 입장완료 버튼
- 노쇼 버튼
- 취소처리 버튼(`WAITING` 상태만)

## 9. API/Route 초안

Thymeleaf 기반 서버 렌더링을 기본으로 한다.

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/` | 웨이팅 등록 화면 |
| `POST` | `/waitings` | 웨이팅 등록 |
| `GET` | `/waitings/{id}` | 사용자 대기 상태 화면 |
| `POST` | `/waitings/{id}/cancel` | 사용자 웨이팅 취소 |
| `GET` | `/admin/waitings` | 관리자 웨이팅 목록 |
| `GET` | `/admin/waitings/entered` | 입장 완료 목록 |
| `POST` | `/admin/waitings/{id}/arrive` | 현장도착 확인 |
| `POST` | `/admin/waitings/{id}/enter` | 입장완료 |
| `POST` | `/admin/waitings/{id}/no-show` | 노쇼 처리 |
| `POST` | `/admin/waitings/{id}/cancel` | 관리자 취소 처리 |
| `POST` | `/admin/waitings/{id}/notify` | 카카오 알림톡 호출 |
| `POST` | `/admin/waitings/notify-shortage` | 부족 인원 일괄 카카오 알림톡 호출 |

## 10. 실행 구성

로컬에서는 Docker 없이 Gradle로 애플리케이션을 실행한다. `SPRING_DATASOURCE_URL`을 지정하지 않으면 `.local-data/` 아래 파일 기반 H2 DB를 사용한다.

```bash
./gradlew bootRun
```

운영에서는 Docker Compose로 애플리케이션과 PostgreSQL을 함께 실행한다.

```yaml
services:
  app:
    image: ${APP_IMAGE}
    container_name: odatour-qms-app
    ports:
      - ${APP_PORT}:8080
    environment:
      TZ: Asia/Seoul
      JAVA_TOOL_OPTIONS: -Duser.timezone=Asia/Seoul
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_H2_CONSOLE_ENABLED: "false"
      SOLAPI_API_KEY: ${SOLAPI_API_KEY}
      SOLAPI_API_SECRET_KEY: ${SOLAPI_API_SECRET_KEY}
      SOLAPI_FROM: ${SOLAPI_FROM}
      SOLAPI_KAKAO_PF_ID: ${SOLAPI_KAKAO_PF_ID}
      SOLAPI_KAKAO_TEMPLATE_ID: ${SOLAPI_KAKAO_TEMPLATE_ID}
      SOLAPI_KAKAO_DISABLE_SMS: ${SOLAPI_KAKAO_DISABLE_SMS:-true}
    depends_on:
      db:
        condition: service_healthy
    restart: unless-stopped

  db:
    image: postgres:16
    container_name: odatour-qms-db
    ports:
      - "${POSTGRES_BIND}:${POSTGRES_PORT}:5432"
    environment:
      TZ: Asia/Seoul
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 10
    restart: unless-stopped

volumes:
  postgres-data:
```

## 11. 환경 변수

| 이름 | 설명 |
| --- | --- |
| `APP_PORT` | 운영 Docker Compose에서 Spring Boot 애플리케이션을 외부에 노출할 포트 |
| `APP_IMAGE` | 운영 Docker Compose에서 실행할 애플리케이션 Docker 이미지 태그 |
| `SPRING_DATASOURCE_URL` | 선택 값. 지정하지 않으면 로컬 H2를 사용하고, 운영 compose에서는 PostgreSQL JDBC URL로 주입된다 |
| `SPRING_DATASOURCE_USERNAME` | 선택 값. 지정하지 않으면 로컬 H2 사용자 `sa`를 사용한다 |
| `SPRING_DATASOURCE_PASSWORD` | 선택 값. 지정하지 않으면 빈 비밀번호를 사용한다 |
| `SPRING_H2_CONSOLE_ENABLED` | H2 콘솔 사용 여부. 운영 compose에서는 `false`로 주입된다 |
| `POSTGRES_BIND` | 운영 PostgreSQL 포트를 바인딩할 호스트 주소. 기본 예시는 `127.0.0.1` |
| `POSTGRES_PORT` | 운영 PostgreSQL 공개 포트 |
| `POSTGRES_DB` | 운영 Docker Compose PostgreSQL DB 이름 |
| `POSTGRES_USER` | 운영 Docker Compose PostgreSQL 사용자 |
| `POSTGRES_PASSWORD` | 운영 Docker Compose PostgreSQL 비밀번호 |
| `SOLAPI_API_KEY` | SOLAPI API Key |
| `SOLAPI_API_SECRET_KEY` | SOLAPI API Secret |
| `SOLAPI_FROM` | SOLAPI 등록 발신번호 |
| `SOLAPI_KAKAO_PF_ID` | 연동한 카카오 비즈니스 채널 pfId |
| `SOLAPI_KAKAO_TEMPLATE_ID` | 승인된 카카오 알림톡 템플릿 ID |
| `SOLAPI_KAKAO_DISABLE_SMS` | 카카오 알림톡 실패 시 SMS 대체 발송 비활성화 여부 |

## 12. 비기능 요구사항

- 박람회 현장 운영 중 데이터 유실을 최소화한다.
- 관리자는 모바일 또는 노트북 브라우저에서 사용할 수 있어야 한다.
- 사용자 화면은 모바일 브라우저 기준으로 동작해야 한다.
- 사용자는 관리자 도움 없이 본인의 웨이팅을 취소할 수 있어야 한다.
- 사용자의 휴대폰 번호는 관리자 화면에서 확인할 수 있어야 한다.
- 카카오 알림톡 발송 실패는 운영자가 확인할 수 있도록 로그로 남긴다.
- 초기 버전에서는 WebSocket, Redis, Kafka, 별도 프론트엔드 서버를 사용하지 않는다.

## 13. 향후 확장 후보

초기 버전에 포함하지 않지만 필요 시 추가할 수 있다.

- 관리자 로그인 강화
- 관리자 처리 이력 테이블
- 카카오 알림톡 발송 이력 테이블
- 일자별 부스/행사 구분
- WebSocket 또는 SSE 기반 실시간 순서 갱신
- Redis 기반 캐시 또는 분산 락
