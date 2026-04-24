# Phase 6 — HTTPS + nginx + Docker Compose 통합

> 목표: nginx를 통해 HTTPS를 적용하고, Spring · MySQL · nginx를 docker-compose로 통합 관리한다.
> EC2 인스턴스를 중지/재시작해도 docker-compose가 자동으로 올라오게 한다.

---

## 전체 구조

```
인터넷
  │ 443 (HTTPS)
  ▼
nginx (docker)          ← SSL 종료, 리버스 프록시
  │ http://spring:8080 (내부 네트워크)
  ▼
Spring Boot (docker)    ← 비즈니스 로직
  │ mysql:3306 (내부 네트워크)
  ▼
MySQL (docker)          ← 데이터 저장
```

외부에서는 443 포트만 열려있고, 8080과 3306은 Docker 내부 네트워크로만 통신한다.
nginx가 SSL을 처리하고 내부 Spring 컨테이너로 요청을 전달하는 **리버스 프록시** 구조다.

---

## 이 방식이 필요한 이유

프론트엔드가 `https://`로 배포되어 있으면 브라우저는 **Mixed Content** 정책에 의해
HTTP API 호출을 차단한다. 백엔드도 HTTPS로 서빙해야 한다.

Spring Boot에 직접 SSL을 붙이는 방법도 있지만, nginx를 앞단에 두는 것이 표준적인 방식이다.
SSL 인증서 갱신, 정적 파일 서빙, 요청 로깅 등을 nginx에서 일괄 처리할 수 있다.

---

## 사전 조건

- EC2에 Docker, Docker Compose가 설치되어 있어야 한다 (Phase 5 참고)
- `~/app/` 디렉토리가 존재해야 한다

---

## 1. DuckDNS 무료 도메인 발급

### DuckDNS가 필요한 이유

EC2는 탄력적 IP 없이 중지/시작할 때마다 퍼블릭 IP가 바뀐다.
Let's Encrypt는 도메인 기반으로 SSL 인증서를 발급하므로 고정 IP 없이는 HTTPS를 적용할 수 없다.

DuckDNS는 **동적 DNS(Dynamic DNS)** 서비스다. 도메인과 IP의 매핑 정보를 저장하고, API를 통해 언제든 IP를 업데이트할 수 있다.

IP는 AWS가 EC2에 부여하는 것이고, DuckDNS는 그 IP를 도메인과 연결해주는 중간 장부 역할이다.

```
처음 등록 시:
  todo-study.duckdns.org → 3.39.187.206   (첫 번째 EC2 IP)

EC2 중지 후 재시작:
  EC2 새 IP = 13.125.xxx.xxx
  duckdns-update.sh 실행 → DuckDNS에 새 IP 신고
  todo-study.duckdns.org → 13.125.xxx.xxx (매핑 자동 갱신)

사용자 입장:
  todo-study.duckdns.org 접속 → 항상 현재 EC2로 연결됨
```

`ip=` 를 비워서 API를 호출하면 DuckDNS가 요청을 보낸 서버의 IP를 자동으로 감지해서 등록한다. EC2에서 스크립트를 실행하면 EC2의 현재 퍼블릭 IP가 자동으로 등록되는 원리다.

**탄력적 IP와 비교:**

| | 탄력적 IP | DuckDNS |
|---|---|---|
| 비용 | 미사용 시 과금 | 무료 |
| IP | 항상 동일한 IP 고정 | 시작할 때마다 IP 바뀜 |
| 접근 방식 | IP 직접 사용 가능 | 도메인으로 접근 |
| DNS 전파 | 즉시 | 수초~수십초 지연 |

탄력적 IP는 IP 자체를 고정하는 방식이고, DuckDNS는 IP가 바뀌어도 도메인이 항상 최신 IP를 가리키도록 유지하는 방식이다.

### 1-1. 도메인 생성

1. https://www.duckdns.org 접속 → GitHub 계정으로 로그인
2. `add domain` 에서 원하는 서브도메인 입력 (예: `todo-study`)
3. 생성 후 **token** 값 복사해두기

생성 결과: `todo-study.duckdns.org` 형태의 도메인이 생긴다.

### 1-2. EC2에 DuckDNS 자동 업데이트 스크립트 설치

EC2가 시작될 때마다 현재 IP를 DuckDNS에 자동으로 알려주는 스크립트다.

```bash
# EC2에 SSH 접속 후
mkdir -p ~/app
cat > ~/app/duckdns-update.sh << 'EOF'
#!/bin/bash
DOMAIN="todo-study"          # DuckDNS 서브도메인 이름 (duckdns.org 앞부분만)
TOKEN="your-duckdns-token"   # DuckDNS 토큰

curl -s "https://www.duckdns.org/update?domains=${DOMAIN}&token=${TOKEN}&ip=" \
  >> ~/app/duckdns.log 2>&1
EOF

chmod +x ~/app/duckdns-update.sh

# 즉시 실행해서 현재 IP 등록
~/app/duckdns-update.sh
```

---

## 2. EC2 보안 그룹 수정

| 타입 | 포트 | 소스 | 용도 |
|------|------|------|------|
| SSH | 22 | 0.0.0.0/0 | GitHub Actions 접속 |
| HTTP | 80 | 0.0.0.0/0 | Let's Encrypt 인증용 |
| HTTPS | 443 | 0.0.0.0/0 | 실제 API 트래픽 |

> 8080 포트는 이제 nginx가 앞단에서 처리하므로 외부에 열 필요가 없다. 제거해도 된다.

---

## 3. 디렉토리 구조 준비

EC2에서 아래 구조로 디렉토리를 생성한다.

```bash
mkdir -p ~/app/nginx/conf.d
mkdir -p ~/app/certbot/conf
mkdir -p ~/app/certbot/www
```

최종 구조:

```
~/app/
├── docker-compose.yml       ← 서비스 정의
├── Dockerfile               ← Spring 컨테이너 빌드 (GitHub Actions가 배포)
├── .env                     ← DB 비밀번호 등 (EC2에서 직접 작성, git에 올리지 않음)
├── duckdns-update.sh        ← IP 자동 업데이트 스크립트
├── nginx/
│   └── conf.d/
│       └── app.conf         ← nginx 설정
├── certbot/
│   ├── conf/                ← Let's Encrypt 인증서 저장 위치
│   └── www/                 ← certbot 인증 챌린지용 webroot
└── todo-spring-*.jar        ← GitHub Actions가 SCP로 배포
```

---

## 4. .env 파일 작성

DB 비밀번호 등 민감한 값을 EC2에 직접 저장한다.
이 파일은 git에 올리지 않는다.

```bash
cat > ~/app/.env << 'EOF'
MYSQL_ROOT_PASSWORD=root1234
DB_USERNAME=todo_user
DB_PASSWORD=todo1234
DDL_AUTO=update
SHOW_SQL=false
EOF
```

---

## 5. docker-compose.yml 수정

기존 MySQL만 있던 파일을 Spring, nginx, certbot을 포함하도록 수정한다.

```yaml
services:

  # ── MySQL ────────────────────────────────────────────────────────────────────
  mysql:
    image: mysql:8.0
    container_name: todo-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: todo_db
      MYSQL_USER: ${DB_USERNAME}
      MYSQL_PASSWORD: ${DB_PASSWORD}
    volumes:
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: always          # EC2 재시작 시 자동으로 컨테이너 재시작
    networks:
      - app-network

  # ── Spring Boot ──────────────────────────────────────────────────────────────
  spring:
    build: .                 # ~/app/Dockerfile 기반으로 이미지 빌드
    container_name: todo-spring
    environment:
      # mysql 컨테이너 이름이 호스트명이 됨 (Docker 내부 DNS)
      DB_URL: jdbc:mysql://mysql:3306/todo_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      DDL_AUTO: ${DDL_AUTO}
      SHOW_SQL: ${SHOW_SQL}
    depends_on:
      mysql:
        condition: service_healthy
    restart: always
    networks:
      - app-network

  # ── nginx ────────────────────────────────────────────────────────────────────
  nginx:
    image: nginx:alpine
    container_name: todo-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/conf.d:/etc/nginx/conf.d        # nginx 설정 파일
      - ./certbot/conf:/etc/letsencrypt          # SSL 인증서
      - ./certbot/www:/var/www/certbot           # certbot 챌린지
    depends_on:
      - spring
    restart: always
    networks:
      - app-network

  # ── certbot (SSL 인증서 발급/갱신용) ─────────────────────────────────────────
  certbot:
    image: certbot/certbot
    volumes:
      - ./certbot/conf:/etc/letsencrypt
      - ./certbot/www:/var/www/certbot

networks:
  app-network:

volumes:
  mysql_data:
```

---

## 6. Dockerfile 작성

GitHub Actions가 SCP로 JAR 파일을 `~/app/`에 전송하면,
docker-compose가 이 Dockerfile로 Spring 컨테이너를 빌드한다.

프로젝트 루트의 `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY *.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

GitHub Actions가 JAR을 배포할 때 이 Dockerfile도 EC2로 전송해야 한다.

---

## 7. nginx 설정

### 7-1. 1단계: HTTP 전용 설정 (SSL 인증서 발급 전)

certbot이 도메인 소유권을 인증할 때 HTTP(80)로 접근하므로, 먼저 HTTP만 설정한다.

```bash
cat > ~/app/nginx/conf.d/app.conf << 'EOF'
server {
    listen 80;
    server_name aidea-bu.duckdns.org;   # 본인 도메인으로 변경

    # certbot 챌린지 요청 처리
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 200 "nginx is running";
    }
}
EOF
```

### 7-2. nginx + MySQL만 먼저 실행

```bash
cd ~/app
docker compose up -d mysql nginx
```

### 7-3. SSL 인증서 발급

```bash
docker compose run --rm certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  --email kangmin1042004@naver.com \   # 본인 이메일
  --agree-tos \
  --no-eff-email \
  -d aidea-bu.duckdns.org          # 본인 도메인
```

성공하면 `~/app/certbot/conf/live/todo-study.duckdns.org/` 에 인증서가 생성된다.

### 7-4. 2단계: HTTPS 설정으로 교체

```bash
cat > ~/app/nginx/conf.d/app.conf << 'EOF'
# HTTP → HTTPS 리다이렉트
server {
    listen 80;
    server_name aidea-bu.duckdns.org;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

# HTTPS
server {
    listen 443 ssl;
    server_name aidea-bu.duckdns.org;

    ssl_certificate /etc/letsencrypt/live/aidea-bu.duckdns.org/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/aidea-bu.duckdns.org/privkey.pem;

    # REST API
    location /api/ {
        proxy_pass http://spring:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket (Y.js 실시간 협업)
    location /ws/ {
        proxy_pass http://spring:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
EOF
```

### 7-5. 전체 서비스 실행

```bash
cd ~/app
docker compose up -d
```

---

## 8. EC2 재시작 시 자동 실행 설정

systemd를 이용해 EC2가 부팅될 때 DuckDNS IP 업데이트와 docker-compose를 자동으로 실행한다.

### 8-1. DuckDNS 업데이트 서비스

```bash
sudo tee /etc/systemd/system/duckdns.service << 'EOF'
[Unit]
Description=DuckDNS IP Update
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/home/ubuntu/app/duckdns-update.sh

[Install]
WantedBy=multi-user.target
EOF
```

### 8-2. docker-compose 자동 시작 서비스

```bash
sudo tee /etc/systemd/system/todo-app.service << 'EOF'
[Unit]
Description=Todo App Docker Compose
After=docker.service duckdns.service
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/home/ubuntu/app
ExecStart=/usr/bin/docker compose up -d
ExecStop=/usr/bin/docker compose down

[Install]
WantedBy=multi-user.target
EOF
```

### 8-3. 서비스 등록 및 활성화

```bash
sudo systemctl daemon-reload
sudo systemctl enable duckdns.service
sudo systemctl enable todo-app.service

# 즉시 실행 테스트
sudo systemctl start duckdns.service
sudo systemctl start todo-app.service

# 상태 확인
sudo systemctl status todo-app.service
```

---

## 9. GitHub Actions 워크플로우 수정

`.github/workflows/deploy.yml` 전체를 아래로 교체한다.

기존 방식과 달라진 점:
- `pkill java` + `nohup java -jar` → `docker compose stop/up --build`로 교체
- JAR과 함께 `Dockerfile`도 EC2로 전송 (docker compose가 빌드에 사용)
- DB 환경변수를 워크플로우에서 주입하지 않음 (EC2의 `.env` 파일이 담당)
- `EC2_HOST`를 DuckDNS 도메인으로 변경

```yaml
name: Deploy to EC2

on:
  push:
    branches: [ main ]
  workflow_dispatch:   # GitHub UI에서 수동 실행 가능

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      # 1. 소스코드 체크아웃
      - name: Checkout
        uses: actions/checkout@v4

      # 2. Java 21 설정
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      # 3. Gradle 빌드 (테스트 제외, 실행 가능한 JAR 생성)
      # Windows에서 커밋하면 gradlew 실행 권한이 사라지는 경우가 있어 명시적으로 부여
      - name: Build with Gradle
        run: |
          chmod +x gradlew
          ./gradlew bootJar -x test

      # 4. SSH 키 설정
      # webfactory/ssh-agent: ED25519 키를 ssh-agent에 안전하게 등록
      - name: Setup SSH agent
        uses: webfactory/ssh-agent@v0.9.0
        with:
          ssh-private-key: ${{ secrets.EC2_SSH_KEY }}

      # 5. 기존 Spring 컨테이너 중지
      - name: Stop existing application
        run: |
          ssh -o StrictHostKeyChecking=no ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }} \
            "cd ~/app && docker compose stop spring || true"

      # 6. JAR 파일 및 Dockerfile 전송
      # 환경변수는 EC2의 .env 파일로 관리하므로 워크플로우에서 주입하지 않음
      - name: Copy files to EC2
        run: |
          scp -o StrictHostKeyChecking=no \
            build/libs/*.jar \
            Dockerfile \
            ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }}:~/app/

      # 7. Spring 컨테이너 재빌드 및 시작
      - name: Start Application
        run: |
          ssh -o StrictHostKeyChecking=no ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }} \
            "cd ~/app && docker compose up -d --build spring"
```

### GitHub Secrets

이제 필요한 Secrets는 3개뿐이다. DB 관련 값은 EC2의 `.env` 파일로 관리하므로 등록할 필요 없다.

| Secret 이름 | 값 | 설명 |
|-------------|-----|------|
| `EC2_HOST` | `todo-study.duckdns.org` | IP 대신 DuckDNS 도메인 |
| `EC2_USERNAME` | `ubuntu` | Ubuntu AMI 기본 유저명 |
| `EC2_SSH_KEY` | `.pem` 파일 전체 내용 | ED25519 개인키 |

기존에 등록해둔 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DDL_AUTO`, `SHOW_SQL` Secrets는 삭제해도 된다.

---

## 10. SSL 인증서 자동 갱신

Let's Encrypt 인증서는 90일마다 만료된다. cron으로 자동 갱신한다.

```bash
# crontab 편집
crontab -e

# 매일 새벽 3시에 갱신 시도 (이미 만료 30일 이내일 때만 실제 갱신됨)
0 3 * * * cd /home/ubuntu/app && docker compose run --rm certbot renew && docker compose exec nginx nginx -s reload >> /home/ubuntu/app/certbot-renew.log 2>&1
```

---

## 11. 동작 확인

```bash
# HTTPS API 호출
curl https://todo-study.duckdns.org/api/todos

# 컨테이너 상태 확인
docker compose ps

# nginx 로그 확인
docker compose logs nginx

# Spring 로그 확인
docker compose logs spring
```

---

## 핵심 개념 정리

### 리버스 프록시란?

클라이언트와 서버 사이에 위치해 요청을 대신 받아 내부 서버로 전달하는 중간 서버다.

```
클라이언트 → nginx(443) → Spring(8080)
```

클라이언트 입장에서는 nginx만 보이고 Spring이 어디서 실행되는지 알 수 없다.
SSL 처리, 로드 밸런싱, 캐싱 등을 nginx에서 담당한다.

### Docker 내부 DNS

같은 docker-compose 네트워크 안에 있는 컨테이너끼리는 **서비스 이름**이 호스트명이 된다.

```yaml
# docker-compose.yml의 서비스 이름이 곧 호스트명
spring:
  environment:
    DB_URL: jdbc:mysql://mysql:3306/todo_db  # 'mysql'이 MySQL 컨테이너의 주소
```

`localhost`가 아닌 `mysql`로 접근해야 한다.

### restart: always

```yaml
restart: always
```

Docker 데몬이 시작될 때(= EC2 부팅 시) 컨테이너를 자동으로 재시작한다.
systemd로 docker-compose를 기동하면, 이 설정이 있는 컨테이너는 자동으로 올라온다.

### EC2 중지/재시작 시 전체 흐름

```
EC2 인스턴스 시작
    ↓
systemd: duckdns.service 실행
    → DuckDNS에 새 IP 등록
    ↓
systemd: todo-app.service 실행
    → docker compose up -d
    ↓
mysql, spring, nginx 컨테이너 자동 시작
    ↓
https://todo-study.duckdns.org 으로 접근 가능
```
