# Odatour Waiting System Requirements

## 1. 목적

박람회 부스 방문자가 QR 코드를 통해 간단히 웨이팅을 등록하고, 관리자가 입장 승인 또는 노쇼 처리를 할 수 있는 웹 기반 웨이팅 시스템을 만든다.

실시간 앱, 별도 모바일 앱, 복잡한 대기열 인프라는 사용하지 않는다. 사용자는 웹 페이지의 새로고침 버튼으로 남은 순서를 확인하고, 입장 순서가 가까워지면 SMS를 받는다.

## 2. 기술 스택

### Backend

- Java 25
- Spring Boot 4.0.6
- Spring Web MVC
- Thymeleaf
- Gradle

### Database

- PostgreSQL
- Docker Compose 기반 로컬/운영 배포

Redis는 초기 버전에서는 사용하지 않는다. 웨이팅 데이터는 유실되면 안 되는 운영 데이터이므로 PostgreSQL을 기준 저장소로 사용한다.

### External Service

- SOLAPI SMS

SMS는 웨이팅 등록, 웨이팅 취소, 입장 승인, 노쇼 처리 이벤트가 발생했을 때 대상자를 조회해서 발송한다. 중복 발송 방지를 위해 발송 완료 시각을 DB에 저장한다.

## 3. 사용자 시나리오

1. 방문자가 부스 QR 코드를 스캔한다.
2. 모바일 웹 페이지에서 휴대폰 번호를 입력한다.
3. 개인정보 수집 및 이용 동의에 체크한다.
4. 웨이팅 리스트에 등록된다.
5. 등록 완료 화면에서 현재 남은 순서를 확인한다.
6. 사용자는 새로고침 버튼을 눌러 남은 순서를 다시 확인한다.
7. 내 앞의 대기 인원이 5명 미만이면 SMS를 받는다.
8. 사용자는 더 이상 대기하지 않을 경우 웨이팅을 취소할 수 있다.
9. 관리자가 입장 승인 또는 노쇼 처리를 한다.

## 4. 관리자 시나리오

1. 관리자는 관리자 페이지에 접속한다.
2. 기본 관리자 페이지에서 현재 처리해야 하는 웨이팅 목록을 등록 순서대로 확인한다.
3. 각 대기자에 대해 입장 승인 처리를 할 수 있다.
4. 각 대기자에 대해 노쇼 처리를 할 수 있다.
5. 입장 승인 또는 노쇼 처리된 사용자는 남은 순서 계산 대상에서 제외된다.
6. 입장 승인 또는 노쇼 처리 후 SMS 발송 대상자를 다시 계산한다.
7. 입장 완료된 사용자는 별도 화면에서 모아서 확인한다.

## 5. 기능 요구사항

### 5.1 웨이팅 등록

- 휴대폰 번호는 필수 입력값이다.
- 개인정보 수집 및 이용 동의는 필수다.
- 동일 휴대폰 번호의 중복 등록 정책은 운영 편의를 위해 다음 중 하나로 정한다.
  - 기본 정책: 진행 중인 웨이팅이 있으면 중복 등록을 막는다.
  - 진행 중 상태: `WAITING`, `CALLED`
- 등록 시 대기 상태는 `WAITING`으로 저장한다.
- 등록 완료 후 사용자 조회 화면으로 이동한다.
- 등록 완료 후 SMS 발송 대상자를 다시 계산한다.

### 5.2 남은 순서 조회

- 사용자는 등록 완료 화면에서 남은 순서를 확인할 수 있다.
- 남은 순서는 조회 시점에 DB 기준으로 계산한다.
- 계산 기준은 나보다 먼저 등록되었고 아직 처리되지 않은 사람 수다.
- 대상 상태는 `WAITING`, `CALLED`다.
- 사용자는 새로고침 버튼으로 최신 순서를 다시 조회한다.

### 5.3 웨이팅 취소

- 사용자는 본인의 대기 상태 화면에서 웨이팅을 취소할 수 있다.
- 취소 가능 상태는 `WAITING`, `CALLED`다.
- 취소 시 상태를 `CANCELED`로 변경한다.
- 취소된 사용자는 남은 순서 계산 대상에서 제외한다.
- 취소된 사용자는 관리자 기본 웨이팅 목록에서 제외한다.
- 취소된 사용자는 관리자 기본 요약 지표에도 포함하지 않는다.
- 취소 완료 후 사용자 화면에는 취소 완료 상태를 표시한다.
- 취소 완료 후 SMS 발송 대상자를 다시 계산한다.

### 5.4 SMS 발송

- SMS 발송 조건은 `남은 순서 < 5`다.
- SMS 발송은 별도 스케줄링 없이 이벤트 기반으로 처리한다.
- SMS 발송 트리거는 웨이팅 등록, 웨이팅 취소, 관리자 입장 승인, 관리자 노쇼 처리다.
- 각 이벤트 처리 후 `WAITING` 상태이면서 `notified_at`이 없는 사용자를 조회한다.
- 각 사용자의 남은 순서를 계산하고, `남은 순서 < 5`에 해당하는 사용자에게 SMS를 발송한다.
- SMS는 한 사용자에게 한 번만 발송한다.
- 중복 발송 방지를 위해 `notified_at`을 저장한다.
- SMS 발송 성공 후 상태를 `CALLED`로 변경한다.
- SMS 발송 실패 시 로그를 남기고, `notified_at`은 저장하지 않는다.
- 초기 버전에서는 별도 메시지 큐 없이 동일 애플리케이션 서비스 로직 안에서 처리한다.

### 5.5 관리자 입장 승인

- 관리자는 대기자를 `ENTERED` 상태로 변경할 수 있다.
- 입장 승인된 사용자는 남은 순서 계산 대상에서 제외한다.
- 처리 후 SMS 발송 대상자를 다시 계산한다.

### 5.6 관리자 노쇼 처리

- 관리자는 대기자를 `NO_SHOW` 상태로 변경할 수 있다.
- 노쇼 처리된 사용자는 남은 순서 계산 대상에서 제외한다.
- 처리 후 SMS 발송 대상자를 다시 계산한다.

## 6. 상태 모델

```text
WAITING -> CALLED -> ENTERED
WAITING -> ENTERED
WAITING -> NO_SHOW
WAITING -> CANCELED
CALLED  -> NO_SHOW
CALLED  -> CANCELED
```

### 상태 정의

| 상태 | 설명 |
| --- | --- |
| `WAITING` | 등록 완료, 아직 호출 전 |
| `CALLED` | SMS 발송 완료, 입장 대기 중 |
| `ENTERED` | 입장 승인 완료 |
| `NO_SHOW` | 노쇼 처리 완료 |
| `CANCELED` | 사용자 취소 완료 |

## 7. 데이터 모델 초안

### waiting_entry

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `id` | bigint | PK |
| `phone_number` | varchar | 휴대폰 번호 |
| `consent_agreed` | boolean | 개인정보 동의 여부 |
| `status` | varchar | 웨이팅 상태 |
| `notified_at` | timestamp | SMS 발송 완료 시각 |
| `entered_at` | timestamp | 입장 승인 시각 |
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
- 새로고침 버튼
- 웨이팅 취소 버튼
- 취소 완료 상태 표시

### 관리자 화면

- 현재 대기 목록
- 기본 화면 `/admin/waitings`에는 `WAITING`, `CALLED` 상태만 표시
- 기본 화면 요약 지표에는 `WAITING`, `CALLED` 상태만 포함
- `CANCELED` 상태는 기본 화면의 요약과 상세 목록에서 제외
- `ENTERED` 상태는 기본 화면의 요약과 상세 목록에서 제외
- 입장 완료된 사용자는 `/admin/waitings/entered` 화면에서 별도로 표시
- 기본 웨이팅 목록은 10명 초과 시 페이징 처리
- 입장 완료 목록은 10명 초과 시 페이징 처리
- 휴대폰 번호 일부 마스킹 표시
- 등록 시각
- 현재 상태
- 남은 순서
- 입장 승인 버튼
- 노쇼 버튼

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
| `POST` | `/admin/waitings/{id}/enter` | 입장 승인 |
| `POST` | `/admin/waitings/{id}/no-show` | 노쇼 처리 |

## 10. Docker Compose 구성

초기 운영 구성은 애플리케이션 1개와 PostgreSQL 1개로 충분하다.

```yaml
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/waiting
      SPRING_DATASOURCE_USERNAME: waiting
      SPRING_DATASOURCE_PASSWORD: waiting
      SOLAPI_API_KEY: ${SOLAPI_API_KEY}
      SOLAPI_API_SECRET: ${SOLAPI_API_SECRET}
      SOLAPI_FROM: ${SOLAPI_FROM}
    depends_on:
      - db

  db:
    image: postgres:16
    environment:
      POSTGRES_DB: waiting
      POSTGRES_USER: waiting
      POSTGRES_PASSWORD: waiting
    volumes:
      - postgres-data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

volumes:
  postgres-data:
```

## 11. 환경 변수

| 이름 | 설명 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 |
| `SOLAPI_API_KEY` | SOLAPI API Key |
| `SOLAPI_API_SECRET` | SOLAPI API Secret |
| `SOLAPI_FROM` | SMS 발신번호 |

## 12. 비기능 요구사항

- 박람회 현장 운영 중 데이터 유실을 최소화한다.
- 관리자는 모바일 또는 노트북 브라우저에서 사용할 수 있어야 한다.
- 사용자 화면은 모바일 브라우저 기준으로 동작해야 한다.
- 사용자는 관리자 도움 없이 본인의 웨이팅을 취소할 수 있어야 한다.
- 사용자의 휴대폰 번호는 관리자 화면에서 일부 마스킹한다.
- SMS 발송 실패는 운영자가 확인할 수 있도록 로그로 남긴다.
- 초기 버전에서는 WebSocket, Redis, Kafka, 별도 프론트엔드 서버를 사용하지 않는다.

## 13. 향후 확장 후보

초기 버전에 포함하지 않지만 필요 시 추가할 수 있다.

- 관리자 로그인 강화
- 관리자 처리 이력 테이블
- SMS 발송 이력 테이블
- 일자별 부스/행사 구분
- WebSocket 또는 SSE 기반 실시간 순서 갱신
- Redis 기반 캐시 또는 분산 락
