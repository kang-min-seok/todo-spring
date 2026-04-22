# Phase 6 — 작업 체크리스트

> 세부 내용은 [phase6-https-nginx.md](phase6-https-nginx.md) 참고

---

## 1. 프로젝트에서 수정할 것

코드를 수정하고 커밋/푸시하면 된다.

- [ ] **`docker-compose.yml` 교체** — spring, nginx, certbot 서비스 추가 (phase6-https-nginx.md 섹션 5 참고)
- [ ] **`Dockerfile` 확인** — 프로젝트 루트에 이미 생성되어 있음
- [ ] **`deploy.yml` 확인** — 이미 docker compose 방식으로 수정되어 있음
- [ ] **`CorsConfig.java` 확인** — `https://todo-study.duckdns.org` (실제 DuckDNS 도메인으로) 추가되어 있는지 확인
- [ ] **`.gitignore` 확인** — `.env`가 포함되어 있는지 확인 (없으면 추가)

---

## 2. EC2에 직접 SSH 접속해서 할 것

아래 작업들은 GitHub Actions 워크플로우로 자동화할 수 없고, EC2에 한 번 직접 접속해서 설정해야 한다.

> **왜 워크플로우로 할 수 없나?**
>
> GitHub Actions는 코드를 빌드하고 배포하는 **반복 작업**을 자동화한다.
> 아래 작업들은 서버를 처음 세팅할 때 **딱 한 번만** 하는 인프라 설정이다.
>
> - `.env` 파일 — DB 비밀번호가 담긴 파일이라 git에 올릴 수 없고, 워크플로우에 값을 넣으면 GitHub Secrets에 다시 의존하게 되어 의미가 없다
> - SSL 인증서 발급 — certbot이 HTTP(80)로 도메인 소유권을 확인하는 과정이 필요하고, 이미 인증서가 있으면 재발급하지 않는다. 매 배포마다 실행할 필요가 없다
> - systemd 등록 — 서버 부팅 설정이라 `sudo` 권한이 필요하고, 한 번 등록하면 끝이다
> - DuckDNS 스크립트 — 토큰이 포함된 민감한 파일이며, 인프라 설정이지 배포 과정이 아니다
> - nginx 설정 — 인증서 발급 전/후 두 단계로 나뉘어 순서가 중요하고, 배포마다 바꿀 필요가 없다

<br>

### 2-1. DuckDNS 설정

- [ ] duckdns.org 가입 → 서브도메인 생성 → 토큰 복사
- [ ] EC2에 `~/app/duckdns-update.sh` 작성 및 즉시 실행 (섹션 1-2)

### 2-2. 보안 그룹 수정

- [ ] AWS 콘솔에서 인바운드 규칙에 HTTP(80), HTTPS(443) 추가 (섹션 2)

### 2-3. 디렉토리 및 설정 파일 생성

- [ ] `~/app/nginx/conf.d/`, `~/app/certbot/conf/`, `~/app/certbot/www/` 디렉토리 생성 (섹션 3)
- [ ] `~/app/.env` 파일 작성 — DB 비밀번호 등 (섹션 4)
- [ ] `~/app/docker-compose.yml` 업로드 — 프로젝트에서 수정한 파일을 scp로 전송

```bash
scp -i your-key.pem docker-compose.yml ubuntu@<EC2_IP>:~/app/
```

### 2-4. SSL 인증서 발급 (최초 1회)

- [ ] nginx HTTP 설정 작성 (섹션 7-1)
- [ ] `docker compose up -d mysql nginx` 실행
- [ ] certbot으로 인증서 발급 (섹션 7-3)
- [ ] nginx HTTPS 설정으로 교체 (섹션 7-4)
- [ ] `docker compose up -d` 전체 실행 (섹션 7-5)

### 2-5. 자동 시작 설정 (최초 1회)

- [ ] `duckdns.service` systemd 등록 (섹션 8-1)
- [ ] `todo-app.service` systemd 등록 (섹션 8-2)
- [ ] `systemctl enable` 및 동작 확인 (섹션 8-3)
- [ ] SSL 인증서 자동 갱신 cron 등록 (섹션 10)

---

## 3. GitHub에서 수정할 것

- [ ] `EC2_HOST` Secret 값을 EC2 퍼블릭 IP → DuckDNS 도메인(`todo-study.duckdns.org`)으로 변경
- [ ] 기존 DB 관련 Secrets(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DDL_AUTO`, `SHOW_SQL`) 삭제

---

## 완료 후 확인

```bash
# HTTPS API 정상 응답 확인
curl https://todo-study.duckdns.org/api/todos

# 컨테이너 상태 확인
docker compose ps
```
