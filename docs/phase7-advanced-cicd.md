# Phase 7 — 무중단 배포 + ECR + 자동 스케줄링 + Discord 알림

> 목표: ECR로 이미지 빌드를 CI 서버로 이전하고, Blue-Green 배포로 무중단을 달성한다.
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
  00:00 KST → Lambda → EC2 중지
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

생성 후 리포지토리 URI 복사: `123456789.dkr.ecr.ap-northeast-2.amazonaws.com/todo-spring`

### 1-2. IAM 사용자 생성 (GitHub Actions용)

GitHub Actions가 ECR에 이미지를 push하려면 AWS 자격증명이 필요하다.

AWS 콘솔 → IAM → Users → Create user

1. 사용자 이름: `github-actions-deploy`
2. 권한 추가 → 직접 정책 연결 → 아래 두 가지 선택
   - `AmazonEC2ContainerRegistryPowerUser` (ECR push/pull 권한)
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

## 2. Dockerfile 수정

GitHub Actions에서 빌드할 때는 JAR을 `app.jar`로 복사한 뒤 이미지를 빌드한다.
`COPY *.jar` 대신 명시적인 파일명을 사용한다.

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 3. GitHub Actions 워크플로우 수정

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

      # ECR 로그인
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ secrets.AWS_REGION }}

      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2

      # GitHub Actions 러너에서 Docker 이미지 빌드 후 ECR push
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

      # EC2에서 Blue-Green 배포 스크립트 실행
      - name: Setup SSH agent
        uses: webfactory/ssh-agent@v0.9.0
        with:
          ssh-private-key: ${{ secrets.EC2_SSH_KEY }}

      # deploy.sh를 프로젝트에서 관리하므로 SCP로 함께 전송
      - name: Copy deploy script to EC2
        run: |
          scp -o StrictHostKeyChecking=no \
            deploy.sh \
            ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }}:~/app/
          ssh -o StrictHostKeyChecking=no ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }} \
            "chmod +x ~/app/deploy.sh"

      - name: Deploy to EC2 (Blue-Green)
        env:
          IMAGE: ${{ steps.build-image.outputs.image }}
        run: |
          ssh -o StrictHostKeyChecking=no ${{ secrets.EC2_USERNAME }}@${{ secrets.EC2_HOST }} \
            "cd ~/app && ./deploy.sh $IMAGE"

      # Discord 알림
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

## 4. Blue-Green 배포 설정

### 4-1. docker-compose.yml 수정

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

### 4-2. nginx upstream 설정 수정

`~/app/nginx/conf.d/app.conf` 상단에 upstream 블록을 추가한다.
배포 시 스크립트가 이 블록을 수정해 트래픽을 전환한다.

```nginx
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
```

### 4-3. deploy.sh 작성 (프로젝트에 추가)

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

# 현재 active 색상 파악 (없으면 blue가 active)
ACTIVE=$(cat ~/app/active_color 2>/dev/null || echo "blue")
if [ "$ACTIVE" = "blue" ]; then
  INACTIVE="green"
else
  INACTIVE="blue"
fi

echo "현재 active: $ACTIVE → 새 배포 대상: $INACTIVE"

# 새 컨테이너 시작
ECR_IMAGE=$NEW_IMAGE docker compose up -d spring-${INACTIVE}

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

### 4-4. EC2 IAM Role 설정

`deploy.sh`에서 EC2가 ECR에서 이미지를 pull하려면 EC2 자체에 IAM Role이 필요하다.

AWS 콘솔 → IAM → Roles → Create role

1. `AWS service` → `EC2` 선택
2. 권한: `AmazonEC2ContainerRegistryReadOnly`
3. 역할 이름: `ec2-ecr-pull-role`
4. EC2 콘솔 → 인스턴스 선택 → Actions → Security → Modify IAM role → 위 Role 연결

---

## 5. EC2 자동 시작/중지 (EventBridge + Lambda)

### 5-1. Lambda 함수 생성

AWS 콘솔 → Lambda → Create function

| 항목 | 값 |
|------|-----|
| 함수 이름 | `ec2-scheduler` |
| 런타임 | Python 3.12 |
| 실행 역할 | 새 역할 생성 |

생성 후 IAM 콘솔에서 자동 생성된 Lambda 실행 역할에 `AmazonEC2FullAccess` 권한 추가.

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

### 5-2. EventBridge 규칙 설정

AWS 콘솔 → EventBridge → Rules → Create rule

**시작 규칙 (08:00 KST = 23:00 UTC 전날)**

| 항목 | 값 |
|------|-----|
| 이름 | `ec2-start-schedule` |
| 스케줄 | `cron(0 23 * * ? *)` |
| 대상 | Lambda: `ec2-scheduler` |
| 입력 | `{"action": "start"}` |

**중지 규칙 (00:00 KST = 15:00 UTC)**

| 항목 | 값 |
|------|-----|
| 이름 | `ec2-stop-schedule` |
| 스케줄 | `cron(0 15 * * ? *)` |
| 대상 | Lambda: `ec2-scheduler` |
| 입력 | `{"action": "stop"}` |

---

## 6. Discord 알림 설정

### 6-1. Discord Webhook 생성

Discord 서버 → 채널 설정 → Integrations → Webhooks → New Webhook → URL 복사

### 6-2. GitHub Secrets 등록

| Secret 이름 | 값 |
|-------------|-----|
| `DISCORD_WEBHOOK` | Discord Webhook URL |

GitHub Actions 알림은 3번 워크플로우에 이미 포함되어 있다.

### 6-3. EC2 시작 시 Discord 알림 (systemd)

EC2가 켜질 때 Discord로 "서버 시작" 알림을 보내는 서비스를 추가한다.

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

## 핵심 개념 정리

### ECR (Elastic Container Registry)

Docker Hub의 AWS 버전이다. 빌드한 이미지를 저장하고 EC2에서 pull해서 실행한다.

```
GitHub Actions (빌드/push)          EC2 (pull/실행)
docker build → ECR push    →    ECR pull → docker run
```

기존에는 EC2에서 직접 빌드했기 때문에 t2.micro의 낮은 사양이 병목이었다.
ECR을 사용하면 빌드는 GitHub Actions 서버(고사양)에서 하고, EC2는 pull만 하면 된다.

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

### EventBridge cron 표현식

AWS EventBridge의 cron은 **UTC 기준**이다. KST(UTC+9)로 변환 필요.

```
cron(분 시 일 월 요일 년)

08:00 KST = 23:00 UTC (전날)
→ cron(0 23 * * ? *)

00:00 KST = 15:00 UTC
→ cron(0 15 * * ? *)
```
