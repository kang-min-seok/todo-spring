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
  2. ./gradlew build (JAR 생성)
  3. SSH로 EC2 접속
  4. JAR 파일 전송 (SCP)
  5. 기존 앱 종료 → 새 JAR 실행
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
| 키 페어 | 새로 생성 → `.pem` 파일 다운로드 후 보관 |
| 스토리지 | 기본 8GB (충분) |

### 1-2. 보안 그룹 (인바운드 규칙)

| 타입 | 포트 | 소스 | 용도 |
|------|------|------|------|
| SSH | 22 | 내 IP | EC2 접속 |
| Custom TCP | 8080 | 0.0.0.0/0 | Spring Boot |
| Custom TCP | 3306 | 내 IP | MySQL (로컬 접속 시) |

> 3306은 운영 환경에서는 외부에 열지 않는 것이 원칙입니다. EC2 내부 통신만 필요하면 인바운드 규칙 불필요.

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

현재 `application.properties`에 DB 비밀번호가 하드코딩되어 있습니다.
EC2에서는 환경변수로 주입받도록 수정합니다.

```properties
# application.properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/todo_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8}
spring.datasource.username=${DB_USERNAME:todo_user}
spring.datasource.password=${DB_PASSWORD:todo1234}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=${DDL_AUTO:update}
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
```

**`${변수명:기본값}` 문법:**
환경변수가 있으면 환경변수 값을, 없으면 `:` 뒤의 기본값을 사용합니다.
로컬 개발 환경에서는 환경변수 없이도 기본값으로 동작하고, EC2에서는 GitHub Secrets로 주입합니다.

---

## 5. GitHub Secrets 등록

GitHub 저장소 → Settings → Secrets and variables → Actions → New repository secret

| Secret 이름 | 값 | 설명 |
|-------------|-----|------|
| `EC2_HOST` | EC2 퍼블릭 IP | 예: `3.34.xxx.xxx` |
| `EC2_USERNAME` | `ubuntu` | AMI 기본 유저명 |
| `EC2_SSH_KEY` | `.pem` 파일 전체 내용 | `-----BEGIN RSA PRIVATE KEY-----` 포함 |
| `DB_URL` | `jdbc:mysql://localhost:3306/todo_db?...` | EC2 내부 MySQL 주소 |
| `DB_USERNAME` | `todo_user` | |
| `DB_PASSWORD` | `todo1234` | |

---

## 6. GitHub Actions 워크플로우

`.github/workflows/deploy.yml` 파일을 생성합니다.

```yaml
name: Deploy to EC2

# main 브랜치에 push될 때 트리거
on:
  push:
    branches: [ main ]

jobs:
  deploy:
    runs-on: ubuntu-latest   # GitHub이 제공하는 임시 Linux 서버에서 실행

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
      - name: Build with Gradle
        run: ./gradlew bootJar -x test

      # 4. EC2에 JAR 전송 및 배포
      - name: Deploy to EC2
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USERNAME }}
          key: ${{ secrets.EC2_SSH_KEY }}
          # envs로 전달한 변수는 script 내에서 $변수명으로 사용 가능
          envs: DB_URL,DB_USERNAME,DB_PASSWORD
          script: |
            # 기존 앱 프로세스 종료 (없으면 무시)
            pkill -f 'todo-spring' || true

            # 잠시 대기 (프로세스가 완전히 종료될 때까지)
            sleep 2

      # 5. JAR 파일 SCP 전송
      - name: Copy JAR to EC2
        uses: appleboy/scp-action@v0.1.7
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USERNAME }}
          key: ${{ secrets.EC2_SSH_KEY }}
          source: "build/libs/*.jar"
          target: "~/app"
          strip_components: 2   # build/libs/ 경로 제거, JAR만 전송

      # 6. EC2에서 새 버전 실행
      - name: Start Application
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USERNAME }}
          key: ${{ secrets.EC2_SSH_KEY }}
          envs: DB_URL,DB_USERNAME,DB_PASSWORD
          script: |
            cd ~/app

            # 환경변수를 주입하며 백그라운드 실행
            # nohup: 터미널이 닫혀도 프로세스 유지
            # &: 백그라운드 실행
            # > app.log 2>&1: stdout + stderr를 app.log에 저장
            nohup env \
              DB_URL="${{ secrets.DB_URL }}" \
              DB_USERNAME="${{ secrets.DB_USERNAME }}" \
              DB_PASSWORD="${{ secrets.DB_PASSWORD }}" \
              java -jar ~/app/todo-spring-*.jar \
              > ~/app/app.log 2>&1 &

            echo "배포 완료"
```

### 워크플로우 파일 구조

```
.github/
└── workflows/
    └── deploy.yml    ← 이 파일을 생성
```

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

```bash
# 로컬 터미널에서
curl http://<EC2_퍼블릭_IP>:8080/api/todos
```

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
GitHub Actions 워크플로우 (envs 또는 ${{ secrets.NAME }})
    ↓
EC2 실행 명령어의 env 블록
    ↓
JVM이 환경변수 읽음
    ↓
Spring Boot가 application.properties의 ${DB_PASSWORD} 해석
```
