# Phase 7 — 무중단 배포 + ECR + 자동 스케줄링 + Discord 알림

> 목표: ECR로 이미지 빌드를 CI 서버로 이전하여 EC2 부하를 줄이고, 빌드된 이미지를 레이어 단위로 효율적으로 전달한다. Blue-Green 배포로 무중단을 달성한다.
> EventBridge로 EC2를 자동 시작/중지하고, Discord로 모든 이벤트 알림을 받는다.

---

## 전체 흐름

```
코드 push → GitHub Actions
  1. JAR 빌드
  2. Docker 이미지 빌드 → ECR push      ← 기존: EC2에서 직접 빌드
  3. EC2에 SSH → deploy.sh 실행
     a. ECR에서 새 이미지 pull
     b. 비활성 컨테이너(Blue/Green)에 새 버전 실행
     c. 헬스체크 통과 시 nginx 트래픽 전환
     d. 기존 컨테이너 종료
  4. Discord 알림 (성공/실패)

EventBridge 스케줄
  08:00 KST → Lambda → EC2 시작
    → systemd: DuckDNS 갱신 + docker compose up
    → Discord 알림 "서버 시작됨"
  20:00 KST → Lambda → EC2 중지
    → Discord 알림 "서버 종료됨"
```

---

## 1. AWS ECR 설정

### 1-1. ECR 리포지토리 생성

AWS 콘솔 → ECR → Create repository

| 항목 | 값 |
|------|-----|
| 이름 | `todo-spring` |
| 가시성 | Private |
| 리전 | ap-northeast-2 (서울) |
| Image tag mutability | Mutable (Re-run 시 동일 태그 덮어쓰기 허용) |

생성 후 리포지토리 URI 복사: `123456789.dkr.ecr.ap-northeast-2.amazonaws.com/todo-spring`

### 1-2. IAM 사용자 생성 (GitHub Actions용)

GitHub Actions 러너는 AWS 외부 서버이므로 ECR에 push하려면 액세스 키가 필요하다.

AWS 콘솔 → IAM → Users → Create user

1. 사용자 이름: `github-actions-deploy`
2. 권한 추가 → 직접 정책 연결 → `AmazonEC2ContainerRegistryPowerUser` 선택
3. 생성 후 → Security credentials → Create access key → `Application running outside AWS` 선택
4. **Access key ID**와 **Secret access key** 복사해두기

### 1-3. GitHub Secrets 등록

| Secret 이름 | 값 |
|-------------|-----|
| `AWS_ACCESS_KEY_ID` | IAM 액세스 키 ID |
| `AWS_SECRET_ACCESS_KEY` | IAM 시크릿 액세스 키 |
| `AWS_REGION` | `ap-northeast-2` |
| `ECR_REPOSITORY` | `todo-spring` |
| `ECR_REGISTRY` | `123456789.dkr.ecr.ap-northeast-2.amazonaws.com` |

---

## 2. EC2 IAM Role 설정

`deploy.sh`에서 EC2가 ECR에서 이미지를 pull하려면 EC2 자체에 IAM Role이 필요하다.
GitHub Actions의 IAM 사용자(액세스 키)와 달리, EC2는 AWS 내부 서버이므로 Role을 직접 붙일 수 있다.

AWS 콘솔 → IAM → Roles → Create role

1. `AWS service` → `EC2` 선택
2. 권한: `AmazonEC2ContainerRegistryReadOnly`
3. 역할 이름: `ec2-ecr-pull-role`
4. EC2 콘솔 → 인스턴스 선택 → Actions → Security → Modify IAM role → 위 Role 연결

---

## 3. EC2 자동 시작/중지 (EventBridge + Lambda)

### 3-1. Lambda 함수 생성

AWS 콘솔 → Lambda → Create function

| 항목 | 값 |
|------|-----|
| 함수 이름 | `ec2-scheduler` |
| 런타임 | Python 3.12 |
| 실행 역할 | 새 역할 생성 |

생성 후 IAM 콘솔에서 자동 생성된 Lambda 실행 역할에 `AmazonEC2FullAccess` 권한 추가.
코드 작성 후 반드시 **Deploy** 버튼을 눌러야 저장된다.

**함수 코드:**

```python
import boto3

ec2 = boto3.client('ec2', region_name='ap-northeast-2')
INSTANCE_ID = 'i-xxxxxxxxxxxxxxxxx'  # 본인 EC2 인스턴스 ID로 변경

def lambda_handler(event, context):
    action = event.get('action')

    if action == 'start':
        ec2.start_instances(InstanceIds=[INSTANCE_ID])
        return {'status': 'started', 'instance': INSTANCE_ID}

    elif action == 'stop':
        ec2.stop_instances(InstanceIds=[INSTANCE_ID])
        return {'status': 'stopped', 'instance': INSTANCE_ID}
```

### 3-2. EventBridge 일정 설정

AWS 콘솔 → EventBridge → Scheduler → 일정 → 일정 생성

**시작 일정 (08:00 KST)**

| 항목 | 값 |
|------|-----|
| 이름 | `ec2-start-schedule` |
| 시간대 | Asia/Seoul |
| Cron | `0 8 * * ? *` |
| 대상 | AWS Lambda - Invoke: `ec2-scheduler` |
| 페이로드 | `{"action": "start"}` |

**중지 일정 (20:00 KST)**

| 항목 | 값 |
|------|-----|
| 이름 | `ec2-stop-schedule` |
| 시간대 | Asia/Seoul |
| Cron | `0 20 * * ? *` |
| 대상 | AWS Lambda - Invoke: `ec2-scheduler` |
| 페이로드 | `{"action": "stop"}` |

---

## 4. Discord 알림 설정

### 4-1. Discord Webhook 생성

Discord 서버 → 채널 설정 → Integrations → Webhooks → New Webhook → URL 복사

### 4-2. GitHub Secrets 등록

| Secret 이름 | 값 |
|-------------|-----|
| `DISCORD_WEBHOOK` | Discord Webhook URL |

### 4-3. EC2 시작 시 Discord 알림 (systemd)

EC2가 켜질 때 Discord로 알림을 보내는 서비스를 추가한다.

```bash
# Discord 알림 스크립트 생성
cat > ~/app/discord-notify.sh << 'EOF'
#!/bin/bash
MESSAGE=$1
WEBHOOK="https://discord.com/api/webhooks/your-webhook-url"  # 본인 URL로 변경

curl -X POST $WEBHOOK \
  -H "Content-Type: application/json" \
  -d "{\"content\": \"$MESSAGE\"}"
EOF

chmod +x ~/app/discord-notify.sh
```

```bash
# systemd 서비스 등록
sudo tee /etc/systemd/system/discord-notify.service << 'EOF'
[Unit]
Description=Discord Startup Notification
After=todo-app.service
Requires=todo-app.service

[Service]
Type=oneshot
ExecStart=/home/ubuntu/app/discord-notify.sh "🟢 서버가 시작되었습니다"

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable discord-notify.service
```

---

## 5. nginx upstream 설정 수정 (EC2)

`~/app/nginx/conf.d/app.conf` 상단에 upstream 블록을 추가한다.
배포 시 `deploy.sh`가 이 블록의 서버 이름을 blue/green으로 교체해 트래픽을 전환한다.

```bash
cat > ~/app/nginx/conf.d/app.conf << 'EOF'
upstream spring {
    server spring-blue:8080;   # deploy.sh가 blue/green으로 교체
}

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

server {
    listen 443 ssl;
    server_name aidea-bu.duckdns.org;

    ssl_certificate /etc/letsencrypt/live/aidea-bu.duckdns.org/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/aidea-bu.duckdns.org/privkey.pem;

    location /api/ {
        # upstream 블록을 사용할 때는 proxy_pass에 포트를 쓰지 않음
        # http://spring:8080 으로 쓰면 nginx가 upstream이 아닌 DNS로 해석해 오류 발생
        proxy_pass http://spring;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/ {
        proxy_pass http://spring;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
EOF
```

nginx 재시작:
```bash
docker exec todo-nginx nginx -s reload
```

---

## 6. deploy.sh 작성 (프로젝트에 추가)

EC2에 직접 생성하면 배포 설정을 바꿀 때마다 EC2에 접속해야 한다.
프로젝트 루트에 파일을 추가하고 GitHub Actions의 SCP 단계에서 함께 전송하면
코드 수정 → 커밋 → 푸시만으로 스크립트도 자동 배포된다.

프로젝트 루트에 `deploy.sh` 파일을 생성한다.

```bash
#!/bin/bash
set -e

NEW_IMAGE=$1

if [ -z "$NEW_IMAGE" ]; then
  echo "Usage: ./deploy.sh <ECR_IMAGE>"
  exit 1
fi

# ECR 로그인 (EC2 IAM Role 사용)
aws ecr get-login-password --region ap-northeast-2 \
  | docker login --username AWS --password-stdin \
    $(echo $NEW_IMAGE | cut -d'/' -f1)

# 모든 docker compose 명령에서 ECR_IMAGE를 사용할 수 있도록 export
# 인라인으로만 설정하면 롤백 시 docker compose 명령에서 변수가 없어 실패함
export ECR_IMAGE=$NEW_IMAGE

# 현재 active 색상 파악 (없으면 blue가 active)
ACTIVE=$(cat ~/app/active_color 2>/dev/null || echo "blue")
if [ "$ACTIVE" = "blue" ]; then
  INACTIVE="green"
else
  INACTIVE="blue"
fi

echo "현재 active: $ACTIVE → 새 배포 대상: $INACTIVE"

# 새 컨테이너 시작
docker compose up -d spring-${INACTIVE}

# 헬스체크 (최대 60초)
echo "헬스체크 중..."
for i in $(seq 1 30); do
  if docker compose exec spring-${INACTIVE} \
      wget -qO- http://localhost:8080/api/todos > /dev/null 2>&1; then
    echo "헬스체크 통과!"
    break
  fi
  if [ $i -eq 30 ]; then
    echo "헬스체크 실패 — 롤백"
    docker compose stop spring-${INACTIVE}
    exit 1
  fi
  sleep 2
done

# nginx upstream 전환
sed -i "s/spring-${ACTIVE}:8080/spring-${INACTIVE}:8080/" \
  ~/app/nginx/conf.d/app.conf
docker compose exec nginx nginx -s reload

# 이전 컨테이너 종료
docker compose stop spring-${ACTIVE}

# active 색상 기록
echo $INACTIVE > ~/app/active_color

echo "배포 완료: $INACTIVE 가 active"
```

---

## 7. Dockerfile 수정

GitHub Actions에서 빌드할 때 JAR을 `app.jar`로 명시적으로 복사한 뒤 이미지를 빌드한다.

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 8. docker-compose.yml 수정

Spring 컨테이너를 Blue/Green 두 벌로 분리한다.
`ECR_IMAGE` 환경변수로 어떤 이미지를 사용할지 제어한다.

```yaml
services:

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
    restart: always
    networks:
      - app-network

  spring-blue:
    image: ${ECR_IMAGE}
    container_name: todo-spring-blue
    environment:
      DB_URL: jdbc:mysql://mysql:3306/todo_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      DDL_AUTO: ${DDL_AUTO}
      SHOW_SQL: ${SHOW_SQL}
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      - app-network

  spring-green:
    image: ${ECR_IMAGE}
    container_name: todo-spring-green
    environment:
      DB_URL: jdbc:mysql://mysql:3306/todo_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      DDL_AUTO: ${DDL_AUTO}
      SHOW_SQL: ${SHOW_SQL}
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      - app-network

  nginx:
    image: nginx:alpine
    container_name: todo-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/conf.d:/etc/nginx/conf.d
      - ./certbot/conf:/etc/letsencrypt
      - ./certbot/www:/var/www/certbot
    restart: always
    networks:
      - app-network

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

## 9. GitHub Actions 워크플로우 수정

```yaml
name: Deploy to EC2

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build with Gradle
        run: |
          chmod +x gradlew
          ./gradlew bootJar -x test

      # 4. AWS 자격증명 설정
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ secrets.AWS_REGION }}

      # 5. ECR 로그인
      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2

      # 6. GitHub Actions 러너(고사양)에서 Docker 이미지 빌드 후 ECR push
      - name: Build and push Docker image to ECR
        id: build-image
        env:
          ECR_REGISTRY: ${{ secrets.ECR_REGISTRY }}
          ECR_REPOSITORY: ${{ secrets.ECR_REPOSITORY }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          cp build/libs/*.jar app.jar
          docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          echo "image=$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG" >> $GITHUB_OUTPUT

      - name: Setup SSH agent
        uses: webfactory/ssh-agent@v0.9.0
        with:
          ssh-private-key: ${{ secrets.EC2_SSH_KEY }}

      # 8. 배포에 필요한 파일 전송 (프로젝트에서 관리)
      # docker-compose.yml도 함께 전송해 EC2의 구성이 항상 최신 상태로 유지됨
      - name: Copy files to EC2
        run: |
          scp -o StrictHostKeyChecking=no \
            deploy.sh \
            docker-compose.yml \
            ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }}:~/app/
          ssh -o StrictHostKeyChecking=no ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }} \
            "chmod +x ~/app/deploy.sh"

      - name: Deploy to EC2 (Blue-Green)
        run: |
          ssh -o StrictHostKeyChecking=no ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }} \
            "cd ~/app && ./deploy.sh ${{ steps.build-image.outputs.image }}"

      - name: Discord 성공 알림
        if: success()
        run: |
          curl -X POST ${{ secrets.DISCORD_WEBHOOK }} \
            -H "Content-Type: application/json" \
            -d '{
              "embeds": [{
                "title": "✅ 배포 성공",
                "description": "'"${{ github.repository }}"' — '"${{ github.ref_name }}"'",
                "color": 3066993
              }]
            }'

      - name: Discord 실패 알림
        if: failure()
        run: |
          curl -X POST ${{ secrets.DISCORD_WEBHOOK }} \
            -H "Content-Type: application/json" \
            -d '{
              "embeds": [{
                "title": "❌ 배포 실패",
                "description": "'"${{ github.repository }}"' — '"${{ github.ref_name }}"'",
                "color": 15158332
              }]
            }'
```

---

## 트러블슈팅

---

### 문제 1 — aws: command not found

**증상:**
```
./deploy.sh: line 12: aws: command not found
```

**원인:** EC2에 AWS CLI가 설치되어 있지 않았다.

**해결:** apt에 패키지가 없으므로 공식 방법으로 설치한다.
```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
```

---

### 문제 2 — ECR 이미지 태그 덮어쓰기 불가

**증상:**
```
tag invalid: The image tag already exists ... cannot be overwritten because the tag is immutable.
```

**원인:** ECR 리포지토리 생성 시 태그 불변성(Immutable)이 기본으로 설정되어 있어, Re-run 시 동일한 커밋 SHA 태그를 덮어쓸 수 없다.

**해결:** AWS 콘솔 → ECR → 리포지토리 → Edit → Image tag mutability → **Mutable**로 변경한다.

---

### 문제 3 — no such service: spring-green

**증상:**
```
no such service: spring-green
```

**원인:** EC2의 `docker-compose.yml`이 구버전(spring 단일 서비스)이었다. 수정된 파일이 EC2에 전달되지 않았다.

**해결:** deploy.yml의 SCP 단계에 `docker-compose.yml`을 추가해 배포 시마다 자동으로 전송한다.
```yaml
scp -o StrictHostKeyChecking=no \
  deploy.sh \
  docker-compose.yml \
  ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }}:~/app/
```

---

### 문제 4 — ECR_IMAGE 미설정으로 롤백 실패

**증상:**
```
The "ECR_IMAGE" variable is not set. Defaulting to a blank string.
service "spring-blue" has neither an image nor a build context specified
```

**원인:** `deploy.sh`에서 `ECR_IMAGE`를 인라인으로만 설정해 롤백 시 `docker compose stop` 실행 시점에 변수가 없어 compose 파일 전체 유효성 검사에서 실패했다.

**해결:** 스크립트 초반에 `export ECR_IMAGE=$NEW_IMAGE`로 전역 설정한다.
```bash
export ECR_IMAGE=$NEW_IMAGE
```

---

### 문제 5 — nginx upstream에 포트 지정 불가

**증상:**
```
upstream "spring" may not have port 8080 in /etc/nginx/conf.d/app.conf:29
```

**원인:** `upstream` 블록을 사용할 때 `proxy_pass http://spring:8080`처럼 포트를 함께 쓰면 nginx가 upstream 이름을 DNS로 해석하려 해서 오류가 발생한다. 포트는 upstream 블록의 `server` 지시어에 이미 정의되어 있으므로 `proxy_pass`에는 이름만 써야 한다.

**해결:** nginx 설정에서 포트를 제거한다.
```nginx
# 잘못된 설정
proxy_pass http://spring:8080;

# 올바른 설정
proxy_pass http://spring;
```

---

## 핵심 개념 정리

### ECR (Elastic Container Registry)

Docker Hub의 AWS 버전이다. 빌드한 이미지를 저장하고 EC2에서 pull해서 실행한다.

```
GitHub Actions (빌드/push)          EC2 (pull/실행)
docker build → ECR push    →    ECR pull → docker run
```

기존에는 EC2에서 직접 빌드했기 때문에 t2.micro의 낮은 사양이 병목이었다.
ECR을 사용하면 빌드는 GitHub Actions 서버(고사양)에서 하고, EC2는 pull만 하면 된다.

ECR은 레이어 단위로 이미지를 관리한다. 코드만 변경된 경우 베이스 이미지 레이어는 이미 ECR에 존재하므로 변경된 앱 레이어만 전송한다. SCP로 전체 이미지를 매번 전송하는 방식보다 반복 배포 시 전송량이 크게 줄어든다.

### Blue-Green 배포

```
배포 전:
  nginx → spring-blue (active)
  spring-green: 중지 상태

배포 중:
  spring-green 시작 (새 버전)
  헬스체크 통과
  nginx → spring-green 으로 전환 (무중단)
  spring-blue 중지

배포 후:
  nginx → spring-green (active)
  spring-blue: 중지 상태 (다음 배포 시 재사용)
```

다운타임이 없는 이유: nginx가 green으로 전환되는 순간 기존 blue는 여전히 살아있어 요청을 처리하다가 전환 완료 후 종료된다.

### EventBridge Scheduler

AWS EventBridge Scheduler는 cron 표현식으로 특정 시간에 Lambda 등의 AWS 서비스를 자동 호출한다. 시간대를 Asia/Seoul로 설정하면 KST 기준으로 직접 입력할 수 있다.
