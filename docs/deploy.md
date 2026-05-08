# Oracle Cloud 배포 설정

이 프로젝트는 GitHub Actions에서 Docker 이미지를 빌드한 뒤 OCI 서버에 SSH로 업로드하고, 서버에서 `docker compose`로 Spring Boot 애플리케이션과 PostgreSQL을 함께 실행한다.

## 구성

| File | Role |
| --- | --- |
| `Dockerfile` | Spring Boot 애플리케이션 컨테이너 이미지 빌드 |
| `docker-compose.yml` | `app`, `db` 서비스를 함께 실행 |
| `.github/workflows/deploy.yml` | 테스트, 이미지 빌드, OCI 서버 업로드, compose 배포 |
| `.env.example` | 서버 `.env`에 필요한 값 예시 |

## GitHub Secrets

Repository Settings > Secrets and variables > Actions에 아래 값을 등록한다.

| Name | Example |
| --- | --- |
| `OCI_HOST` | `123.123.123.123` |
| `OCI_USER` | `ociax` |
| `OCI_SSH_PRIVATE_KEY` | 배포 서버 접속용 private key 전체 내용 |
| `OCI_PORT` | SSH 포트. 기본값이 22여도 현재 workflow에서는 값을 등록한다 |

## 서버 준비

서버에는 Docker Engine과 Docker Compose plugin이 설치되어 있어야 한다.

```bash
docker --version
docker compose version
```

배포 디렉터리는 workflow의 `DEPLOY_DIR` 값과 맞춘다.

```bash
mkdir -p /home/ociax/app
```

배포 사용자가 `docker` 명령을 실행할 수 있어야 한다. 일반적으로 배포 사용자를 `docker` 그룹에 추가한다.

```bash
sudo usermod -aG docker ociax
```

그룹 변경 후에는 SSH 세션을 다시 접속해야 한다.

## 서버 환경 변수

서버의 `/home/ociax/app/.env`를 만든다. `.env.example`을 기준으로 실제 값을 채운다.

```properties
APP_PORT=8080
APP_IMAGE=odatour-waiting-system:latest

POSTGRES_BIND=127.0.0.1
POSTGRES_PORT=5432
POSTGRES_DB=odatour
POSTGRES_USER=odatour
POSTGRES_PASSWORD=change-me
```

`POSTGRES_BIND=127.0.0.1`이면 PostgreSQL 포트는 서버 내부에서만 열린다. 외부에서 DB에 직접 접속해야 하는 운영 요구가 없다면 이 값이 더 안전하다.

## 배포 흐름

`main` 브랜치에 push하거나 workflow를 수동 실행하면 아래 순서로 진행된다.

| Job | Role |
| --- | --- |
| `Build` | 테스트 실행, Docker 이미지 빌드, 이미지 tar artifact 업로드 |
| `Deploy` | OCI 서버에 `docker-compose.yml`과 이미지 tar 업로드, `docker load`, `docker compose up -d` 실행 |

서버에서 수동으로 상태를 확인하려면:

```bash
cd /home/ociax/app
docker compose ps
docker compose logs -f app
```

수동 재시작은 아래처럼 한다.

```bash
cd /home/ociax/app
docker compose restart app
```

## 방화벽

Spring Boot 기본 포트를 사용한다면 서버 방화벽에서 `APP_PORT` 값을 연다.

```bash
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
sudo firewall-cmd --list-ports
```

Oracle Cloud Security List 또는 Network Security Group에서도 SSH 포트와 애플리케이션 접근 포트를 허용해야 한다.
