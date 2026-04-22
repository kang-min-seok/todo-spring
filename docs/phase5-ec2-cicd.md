# Phase 5 — AWS EC2 배포 + GitHub Actions CI/CD

> 목표: Spring Boot 백엔드를 AWS EC2에 배포하고, main 브랜치에 push하면 자동으로 EC2에 배포되는 CI/CD 파이프라인 구성

---

## 전체 흐름

```
로컬에서 코드 작성
        ↓
git push → main 브랜치
        ↓
GitHub Actions 트리거
  1. 코드 체크아웃
  2. ./gradlew bootJar (JAR 생성)
  3. webfactory/ssh-agent로 ED25519 키 등록
  4. SSH로 기존 앱 종료
  5. SCP로 JAR 파일 전송
  6. SSH로 새 JAR 실행
        ↓
EC2에서 Spring Boot 앱이 새 버전으로 실행
```

---

## 1. EC2 인스턴스 생성

### 1-1. 인스턴스 설정

AWS 콘솔 → EC2 → Launch Instance

| 항목 | 값 |
|------|-----|
| AMI | Ubuntu Server 24.04 LTS |
| 인스턴스 타입 | t2.micro (프리 티어) |
| 키 페어 | 새로 생성 (ED25519) → `.pem` 파일 다운로드 후 보관 |
| 스토리지 | 기본 8GB (충분) |

### 1-2. 보안 그룹 (인바운드 규칙)

| 타입 | 포트 | 소스 | 용도 |
|------|------|------|------|
| SSH | 22 | 0.0.0.0/0 | GitHub Actions → EC2 접속 |
| Custom TCP | 8080 | 0.0.0.0/0 | Spring Boot API |
| Custom TCP | 3306 | 내 IP | MySQL (로컬 접속 시) |

> 3306은 운영 환경에서는 외부에 열지 않는 것이 원칙입니다. EC2 내부 통신만 필요하면 인바운드 규칙 불필요.

> SSH 포트(22)를 0.0.0.0/0으로 열어야 GitHub Actions 러너(매번 IP가 바뀌는 임시 서버)가 접속할 수 있습니다.

---

## 2. EC2 초기 설정

SSH로 EC2에 접속합니다.

```bash
# 키 파일 권한 설정 (최초 1회)
chmod 400 your-key.pem

# EC2 접속
ssh -i your-key.pem ubuntu@<EC2_퍼블릭_IP>
```

### 2-1. Java 21 설치

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk

# 설치 확인
java -version
# openjdk version "21.x.x" ...
```

### 2-2. Docker + Docker Compose 설치

MySQL을 기존 `docker-compose.yml`로 그대로 띄울 수 있습니다.

```bash
# Docker 설치
sudo apt install -y docker.io
sudo systemctl enable docker
sudo systemctl start docker

# ubuntu 유저가 sudo 없이 docker 사용 가능하도록
sudo usermod -aG docker ubuntu

# 재로그인 후 적용 (접속 끊고 다시 SSH 접속)
exit
```

```bash
# Docker Compose 플러그인 설치
sudo apt install -y docker-compose-plugin

# 설치 확인
docker compose version
```

### 2-3. 앱 디렉토리 생성

```bash
mkdir -p ~/app
```

---

## 3. EC2에서 MySQL 실행

로컬의 `docker-compose.yml`을 EC2에 올려 MySQL 컨테이너를 실행합니다.

```bash
# EC2에 docker-compose.yml 전송 (로컬 터미널에서 실행)
scp -i your-key.pem docker-compose.yml ubuntu@<EC2_IP>:~/app/

# EC2에서 MySQL 컨테이너 실행
cd ~/app
docker compose up -d

# 실행 확인
docker compose ps
```

---

## 4. 환경변수 분리 — `application.properties` 수정

`application.properties`에 DB 비밀번호가 하드코딩되어 있으면 GitHub에 그대로 노출됩니다.
EC2에서는 환경변수로 주입받도록 수정합니다.

```properties
# application.properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/todo_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8}
spring.datasource.username=${DB_USERNAME:todo_user}
spring.datasource.password=${DB_PASSWORD:todo1234}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=${DDL_AUTO:update}
spring.jpa.show-sql=${SHOW_SQL:true}
spring.jpa.properties.hibernate.format_sql=${SHOW_SQL:true}
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
```

**`${변수명:기본값}` 문법:**
환경변수가 있으면 환경변수 값을, 없으면 `:` 뒤의 기본값을 사용합니다.
로컬 개발 환경에서는 환경변수 없이도 기본값으로 동작하고, EC2에서는 GitHub Secrets로 주입합니다.

---

## 5. GitHub Secrets 등록

GitHub 저장소 → Settings → Secrets and variables → Actions → New repository secret

Secret 하나당 "New repository secret" 버튼을 눌러 개별 등록합니다.

| Secret 이름 | 값 | 설명 |
|-------------|-----|------|
| `EC2_HOST` | EC2 퍼블릭 IP | 예: `3.34.xxx.xxx` |
| `EC2_USERNAME` | `ubuntu` | Ubuntu AMI 기본 유저명 |
| `EC2_SSH_KEY` | `.pem` 파일 전체 내용 | `-----BEGIN OPENSSH PRIVATE KEY-----` 포함 전체 |
| `DB_URL` | `jdbc:mysql://localhost:3306/todo_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8` | EC2 내부 MySQL 주소 |
| `DB_USERNAME` | `todo_user` | |
| `DB_PASSWORD` | `todo1234` | |
| `DDL_AUTO` | `update` | |
| `SHOW_SQL` | `false` | EC2에서는 SQL 로그 불필요 |

**EC2_SSH_KEY 등록 방법:**
`.pem` 파일을 텍스트 에디터로 열어 전체 내용을 그대로 복붙합니다.

```
-----BEGIN OPENSSH PRIVATE KEY-----
(키 내용)
-----END OPENSSH PRIVATE KEY-----
```

마지막 줄 이후 개행이 하나 있어야 합니다.

---

## 6. GitHub Actions 워크플로우

`.github/workflows/deploy.yml`

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
      # 이후 ssh/scp 명령은 -i 없이도 자동으로 이 키를 사용함
      - name: Setup SSH agent
        uses: webfactory/ssh-agent@v0.9.0
        with:
          ssh-private-key: ${{ secrets.EC2_SSH_KEY }}

      # 5. 기존 앱 프로세스 종료
      - name: Stop existing application
        run: |
          ssh -o StrictHostKeyChecking=no ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }} \
            "pkill java || true; sleep 1"

      # 6. JAR 파일 전송
      - name: Copy JAR to EC2
        run: |
          scp -o StrictHostKeyChecking=no \
            build/libs/*.jar \
            ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }}:~/app/

      # 7. EC2에서 새 버전 실행
      - name: Start Application
        run: |
          ssh -o StrictHostKeyChecking=no ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }} << 'EOF'
            nohup env \
              DB_URL="${{ secrets.DB_URL }}" \
              DB_USERNAME="${{ secrets.DB_USERNAME }}" \
              DB_PASSWORD="${{ secrets.DB_PASSWORD }}" \
              DDL_AUTO="${{ secrets.DDL_AUTO }}" \
              SHOW_SQL="${{ secrets.SHOW_SQL }}" \
              java -jar ~/app/todo-spring-*.jar \
              > ~/app/app.log 2>&1 &
            echo "배포 완료"
          EOF
```

### workflow_dispatch란?

`workflow_dispatch`를 추가하면 GitHub 저장소 → Actions 탭에서 워크플로우를 수동으로 실행할 수 있습니다.
특정 브랜치를 선택해 실행할 수 있어서, main 머지 없이 테스트할 때 유용합니다.

---

## 7. 배포 확인

### 앱 실행 상태 확인

```bash
# EC2에서 실행 중인 프로세스 확인
ps aux | grep java

# 앱 로그 확인 (실시간)
tail -f ~/app/app.log
```

### API 동작 확인

로컬 터미널에서 바로 날립니다. EC2 퍼블릭 IP는 인터넷에 공개된 주소라 어디서든 접근 가능합니다.

```bash
curl http://<EC2_퍼블릭_IP>:8080/api/todos
```

응답이 `[]`이면 정상 배포된 것입니다.

---

## 8. 전체 디렉토리 구조

```
.github/
└── workflows/
    └── deploy.yml              ← CI/CD 파이프라인 정의
src/
└── main/
    └── resources/
        └── application.properties  ← 환경변수 참조로 변경
docker-compose.yml              ← EC2에도 동일하게 사용 (MySQL)
```

---

## 핵심 개념 정리

### GitHub Actions란?

GitHub 저장소에 이벤트(push, PR 등)가 발생하면 자동으로 지정한 작업을 실행하는 CI/CD 플랫폼입니다.
`runs-on: ubuntu-latest`는 GitHub이 임시 Linux 서버를 하나 만들어 그 위에서 빌드/배포를 실행한다는 의미입니다.

**NestJS 비교:**
NestJS 프로젝트에서 GitHub Actions로 빌드/배포하는 방식과 구조가 동일합니다.
Spring Boot는 JAR 파일 하나에 Tomcat이 내장되어 있어서 별도 웹서버 설치 없이 `java -jar`만으로 실행됩니다.

### nohup이란?

```bash
nohup java -jar app.jar > app.log 2>&1 &
```

| 부분 | 의미 |
|------|------|
| `nohup` | SSH 세션이 닫혀도 프로세스를 유지 |
| `> app.log` | 표준 출력을 파일에 저장 |
| `2>&1` | 표준 에러도 같은 파일에 저장 |
| `&` | 백그라운드 실행 |

### 환경변수 주입 흐름

```
GitHub Secrets
    ↓
GitHub Actions 워크플로우 (${{ secrets.NAME }})
    ↓
EC2 실행 명령어의 env 블록
    ↓
JVM이 환경변수 읽음
    ↓
Spring Boot가 application.properties의 ${DB_PASSWORD} 해석
```

---

## 트러블슈팅

---

### 문제 1 — gradlew Permission denied (exit code 126)

**증상:**

```
Run chmod +x gradlew
./gradlew bootJar -x test
/usr/bin/bash: line 1: ./gradlew: Permission denied
Error: Process exited with code 126
```

**원인:**
Windows에서 커밋하면 `gradlew`의 Unix 실행 권한(`chmod +x`)이 사라집니다.
GitHub Actions 러너는 Linux이므로 실행 권한이 없으면 Permission denied가 납니다.

**수정:**
빌드 전에 명시적으로 실행 권한을 부여합니다.

```yaml
- name: Build with Gradle
  run: |
    chmod +x gradlew
    ./gradlew bootJar -x test
```

---

### 문제 2 — appleboy/ssh-action ED25519 키 호환성 문제 (exit code 143)

**증상:**

```
err: Process exited with status 143 from signal TERM
```

**원인:**
`appleboy/ssh-action`이 AWS에서 생성한 ED25519 OpenSSH 형식 키(`-----BEGIN OPENSSH PRIVATE KEY-----`)를 제대로 처리하지 못합니다.
내부적으로 Go의 crypto 라이브러리를 사용하는데, ED25519 키 파싱에서 호환성 문제가 있습니다.

**수정:**
`appleboy/ssh-action`과 `appleboy/scp-action`을 제거하고, `webfactory/ssh-agent`로 키를 등록한 뒤 네이티브 `ssh`/`scp` 명령을 직접 사용합니다.

```yaml
# 변경 전 (appleboy)
- name: Deploy to EC2
  uses: appleboy/ssh-action@v1
  with:
    host: ${{ secrets.EC2_HOST }}
    key: ${{ secrets.EC2_SSH_KEY }}
    script: |
      pkill -f 'todo-spring' || true

# 변경 후 (webfactory + 네이티브 ssh)
- name: Setup SSH agent
  uses: webfactory/ssh-agent@v0.9.0
  with:
    ssh-private-key: ${{ secrets.EC2_SSH_KEY }}

- name: Stop existing application
  run: |
    ssh -o StrictHostKeyChecking=no ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }} \
      "pkill java || true; sleep 1"
```

---

### 문제 3 — SSH 연결 실패 (exit code 255) — known_hosts 문제

**증상:**

```
Warning: Permanently added '***' (ED25519) to the list of known hosts.
Error: Process completed with exit code 255.
```

**원인:**
`ssh-keyscan`으로 known_hosts를 구성하는 방식이 불안정해, 호스트 키 검증에서 실패했습니다.

**수정:**
`StrictHostKeyChecking=no` 옵션으로 known_hosts 검증을 생략합니다.
학습용 환경에서는 허용 가능한 설정입니다.

```bash
ssh -o StrictHostKeyChecking=no $EC2_USERNAME@$EC2_HOST "..."
scp -o StrictHostKeyChecking=no build/libs/*.jar $EC2_USERNAME@$EC2_HOST:~/app/
```

---

### 문제 4 — pkill self-kill 문제 (exit code 255)

**증상:**

SSH 인증은 성공했는데 명령 실행 후 exit-signal이 오면서 exit code 255로 실패합니다.

```
debug1: Sending command: pkill -f 'todo-spring' || true && sleep 2
debug1: client_input_channel_req: channel 0 rtype exit-signal reply 0
debug1: Exit status -1
Error: Process completed with exit code 255.
```

**원인:**
`pkill -f` 옵션은 실행 중인 모든 프로세스의 **전체 커맨드라인**을 스캔합니다.
`pkill -f 'todo-spring'`을 실행하면 pkill 프로세스 자신의 argv에도 `todo-spring`이 포함되어 있어서, pkill이 자기 자신에게 SIGTERM을 보내 종료됩니다.
프로세스가 시그널로 종료되면 `|| true`로는 잡을 수 없어 SSH가 exit-signal을 수신하고 exit code 255로 반환합니다.

```
pkill -f 'todo-spring' 실행
    → 프로세스 스캔: "pkill -f todo-spring" ← 자기 자신 argv에 'todo-spring' 포함
    → 자기 자신에게 SIGTERM 전송
    → pkill 프로세스 비정상 종료 (exit-signal)
    → || true 로 잡히지 않음
    → SSH exit code 255
```

`pkill -f 'java -jar'`로 바꿔도 동일한 문제가 발생합니다. argv에 `java -jar`가 그대로 들어있기 때문입니다.

**수정:**
`-f` 없이 프로세스 이름만 매칭합니다. `pkill java`는 이름이 `java`인 프로세스만 종료하며, pkill 자신의 이름은 `pkill`이므로 절대 self-kill이 발생하지 않습니다.

```bash
# 변경 전 (self-kill 발생)
pkill -f 'todo-spring' || true

# 변경 후 (self-kill 없음)
pkill java || true; sleep 1
```

Spring Boot는 `java -jar app.jar`로 실행되므로 프로세스 이름이 `java`입니다.
`pkill java`는 이름이 정확히 `java`인 프로세스만 종료해 안전합니다.
