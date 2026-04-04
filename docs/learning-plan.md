# Spring Boot 학습 계획 - Todo 서비스 백엔드

> 대상: NestJS/TypeScript 경험 있는 프론트엔드 개발자 지망생
> 목표: Spring Boot + JPA + WebSocket + Docker CI/CD 실전 학습

---

## 전체 로드맵

```
Phase 1: Spring Boot 기본 구조 & REST API
    ↓
Phase 2: Spring Data JPA & MySQL 연동
    ↓
Phase 3: WebSocket 실시간 통신
    ↓
Phase 4: Docker & Docker Compose
    ↓
Phase 5: GitHub Actions CI/CD
```

---

## Phase 1 — Spring Boot 기본 구조 & REST API

### 학습 목표
- Spring Boot 프로젝트 구조 이해 (NestJS와 비교)
- IoC(Inversion of Control), DI(Dependency Injection) 개념 이해
- 레이어드 아키텍처 (Controller → Service → Repository) 구현
- REST API CRUD 구현 (인메모리 데이터 사용)
- DTO 패턴, 유효성 검사(@Valid), 예외 처리(@ControllerAdvice)

### 핵심 개념

| 개념 | Spring | NestJS | 설명 |
|------|--------|--------|------|
| 진입점 | `@SpringBootApplication` + `main()` | `NestFactory.create()` | 앱 부트스트랩 |
| IoC 컨테이너 | `ApplicationContext` | NestJS Module System | Bean/Provider 관리 |
| DI | 생성자 주입 (권장) | `@Inject()` / 생성자 | 의존성 자동 주입 |
| HTTP 컨트롤러 | `@RestController` | `@Controller` | 요청 라우팅 |
| 라우팅 | `@GetMapping`, `@PostMapping` 등 | `@Get()`, `@Post()` 등 | HTTP 메서드 매핑 |
| 비즈니스 로직 | `@Service` | `@Injectable()` Service | 서비스 레이어 |
| 유효성 검사 | `@Valid` + Bean Validation | `class-validator` + `ValidationPipe` | 요청 검증 |
| 예외 처리 | `@ControllerAdvice` | `ExceptionFilter` | 전역 에러 핸들링 |
| 응답 직렬화 | Jackson (자동) | class-transformer | 객체 → JSON |

### 구현 목록
- [ ] `TodoController` - CRUD 엔드포인트 (GET/POST/PUT/DELETE)
- [ ] `TodoService` - 비즈니스 로직 (인메모리 List 사용)
- [ ] `Todo` DTO - `CreateTodoRequest`, `UpdateTodoRequest`, `TodoResponse`
- [ ] `GlobalExceptionHandler` - `@ControllerAdvice`로 전역 예외 처리
- [ ] `CustomException` - 커스텀 에러 클래스

### 완료 조건
- `GET /api/todos` — 전체 조회
- `GET /api/todos/{id}` — 단건 조회
- `POST /api/todos` — 생성
- `PUT /api/todos/{id}` — 수정
- `DELETE /api/todos/{id}` — 삭제
- `PATCH /api/todos/{id}/complete` — 완료 처리

### 산출 문서
→ `docs/phase1-spring-basics.md`

---

## Phase 2 — Spring Data JPA & MySQL 연동

### 학습 목표
- JPA(Java Persistence API)와 Hibernate 관계 이해
- `@Entity`, `@Table`, `@Column` 등 매핑 어노테이션 학습
- Spring Data JPA `JpaRepository` 인터페이스 활용
- JPQL과 메서드 네이밍 쿼리 학습
- `@Transactional` 트랜잭션 처리 이해
- MySQL Docker 컨테이너와 연동
- DB 마이그레이션 (Flyway 또는 Liquibase) 기초

### 핵심 개념

| 개념 | Spring JPA | TypeORM (NestJS) | 설명 |
|------|-----------|-----------------|------|
| 엔티티 | `@Entity` | `@Entity()` | DB 테이블 매핑 |
| 기본키 | `@Id` + `@GeneratedValue` | `@PrimaryGeneratedColumn()` | PK 설정 |
| 컬럼 | `@Column` | `@Column()` | 필드-컬럼 매핑 |
| 생성/수정 시간 | `@CreatedDate`, `@LastModifiedDate` | `@CreateDateColumn()` | 자동 시간 기록 |
| Repository | `JpaRepository<T, ID>` | `Repository<T>` | CRUD 기본 제공 |
| 커스텀 쿼리 | JPQL `@Query` / 메서드 네이밍 | `@Query()` / QueryBuilder | 직접 쿼리 작성 |
| 트랜잭션 | `@Transactional` | `QueryRunner` / `@Transaction` | 트랜잭션 관리 |
| 연관관계 | `@ManyToOne`, `@OneToMany` | `@ManyToOne()`, `@OneToMany()` | 테이블 관계 |
| 지연/즉시 로딩 | LAZY / EAGER | `{ lazy: true }` | 연관 데이터 로딩 전략 |

### 구현 목록
- [ ] `Todo` Entity 클래스 작성
- [ ] `TodoRepository` — `JpaRepository` 상속
- [ ] `application.properties` MySQL 연결 설정
- [ ] `TodoService` 인메모리 → JPA 기반으로 교체
- [ ] 페이지네이션 (`Pageable`)
- [ ] 검색 기능 (제목으로 검색 - JPQL 쿼리)
- [ ] `docker-compose.yml` MySQL 서비스 추가 (개발용)

### 완료 조건
- MySQL DB에 데이터 영속화
- 서버 재시작 후에도 데이터 유지
- 페이지네이션 응답 구현

### 산출 문서
→ `docs/phase2-jpa-mysql.md`

---

## Phase 3 — WebSocket 실시간 통신

### 학습 목표
- WebSocket과 HTTP의 차이점 이해
- STOMP(Simple Text Oriented Messaging Protocol) 프로토콜 이해
- Spring WebSocket + STOMP 서버 구현
- 클라이언트 구독/발행 모델 학습
- Todo 변경 시 연결된 클라이언트에 실시간 브로드캐스트

### 핵심 개념

| 개념 | Spring WebSocket | NestJS Gateway |
|------|-----------------|----------------|
| 설정 | `@EnableWebSocketMessageBroker` | `@WebSocketGateway()` |
| 메시지 핸들러 | `@MessageMapping` | `@SubscribeMessage()` |
| 브로드캐스트 | `SimpMessagingTemplate` | `server.emit()` |
| 구독 경로 | `/topic/**` | 네임스페이스/룸 |
| 발행 경로 | `/app/**` | 이벤트명 |

### STOMP 통신 흐름
```
클라이언트 → CONNECT → WebSocket 연결 수립
클라이언트 → SUBSCRIBE /topic/todos → 구독 등록
클라이언트 → SEND /app/todos.add → 메시지 발행
서버 → STOMP 브로커 → /topic/todos → 구독자 전체에게 브로드캐스트
```

### 구현 목록
- [ ] `WebSocketConfig` — STOMP 브로커 설정
- [ ] `TodoWebSocketHandler` — 메시지 수신/발신 처리
- [ ] Todo CRUD 발생 시 WebSocket으로 변경 이벤트 브로드캐스트
- [ ] 연결/해제 이벤트 처리
- [ ] WebSocket 연결 테스트 (curl or simple HTML 클라이언트)

### 이벤트 설계
```json
// 클라이언트가 받는 이벤트 (서버 → 클라이언트)
{
  "type": "TODO_CREATED" | "TODO_UPDATED" | "TODO_DELETED" | "TODO_COMPLETED",
  "payload": { /* Todo 데이터 */ },
  "timestamp": "2024-01-01T00:00:00Z"
}
```

### 완료 조건
- HTTP API로 Todo 생성/수정/삭제 시 WebSocket 구독자에게 실시간 알림
- 다수의 클라이언트 동시 연결 처리

### 산출 문서
→ `docs/phase3-websocket.md`

---

## Phase 4 — Docker & Docker Compose

### 학습 목표
- Docker 이미지와 컨테이너 개념 이해
- Spring Boot 앱 Dockerfile 작성 (멀티 스테이지 빌드)
- Docker Compose로 앱 + MySQL + (선택) Redis 오케스트레이션
- 환경변수 분리 (`.env` 파일 활용)
- 헬스체크, 의존성 순서 설정

### Docker Compose 구성

```yaml
services:
  app:          # Spring Boot 애플리케이션
  mysql:        # MySQL 8.x 데이터베이스
  # (선택) redis: # 세션/캐시용
```

### 구현 목록
- [ ] `Dockerfile` — 멀티 스테이지 빌드 (build → runtime)
- [ ] `docker-compose.yml` — 전체 서비스 정의
- [ ] `docker-compose.override.yml` — 개발 환경 오버라이드
- [ ] `.env.example` — 환경변수 템플릿
- [ ] MySQL 초기화 스크립트
- [ ] 헬스체크 설정 (앱이 MySQL 준비 후 시작)

### 완료 조건
- `docker compose up -d` 한 번으로 전체 서비스 실행
- 환경변수로 DB 접속 정보 주입
- 컨테이너 재시작 시 데이터 유지 (볼륨 마운트)

### 산출 문서
→ `docs/phase4-docker.md`

---

## Phase 5 — GitHub Actions CI/CD

### 학습 목표
- CI(지속적 통합)와 CD(지속적 배포) 개념 이해
- GitHub Actions 워크플로우 작성
- PR 시 자동 테스트 실행 (CI)
- main 브랜치 푸시 시 Docker 이미지 빌드 & 배포 (CD)
- GitHub Container Registry(GHCR) 활용

### 워크플로우 설계

```
PR 오픈/업데이트 → CI 파이프라인
  ├── 코드 체크아웃
  ├── Java 17 설정
  ├── Gradle 캐시
  └── 테스트 실행 (./gradlew test)

main 브랜치 머지 → CD 파이프라인
  ├── Docker 이미지 빌드
  ├── GHCR에 이미지 푸시
  └── (선택) 서버 배포 트리거
```

### 구현 목록
- [ ] `.github/workflows/ci.yml` — PR 자동 테스트
- [ ] `.github/workflows/cd.yml` — Docker 이미지 빌드 & 푸시
- [ ] Gradle 테스트 작성 (기본 CRUD 통합 테스트)
- [ ] `@SpringBootTest` 테스트 클래스 작성

### 완료 조건
- PR 생성 시 테스트 자동 실행, 실패 시 머지 불가
- main 머지 시 Docker 이미지 자동 빌드

### 산출 문서
→ `docs/phase5-cicd.md`

---

## 학습 체크리스트

### Spring Core
- [ ] Spring Bean 생명주기 이해
- [ ] IoC 컨테이너 (ApplicationContext) 이해
- [ ] 의존성 주입 3가지 방법 (생성자/세터/필드) + 권장 방식 이해
- [ ] `@Component`, `@Service`, `@Repository`, `@Controller` 차이
- [ ] `@Configuration` + `@Bean` 직접 Bean 등록

### Spring MVC
- [ ] DispatcherServlet 요청 처리 흐름 이해
- [ ] `@RequestBody`, `@PathVariable`, `@RequestParam` 활용
- [ ] `ResponseEntity<T>` 응답 커스텀
- [ ] 인터셉터(Interceptor) vs 필터(Filter)

### Spring Data JPA
- [ ] JPA 영속성 컨텍스트 (1차 캐시, 변경 감지)
- [ ] 엔티티 생명주기 (비영속/영속/준영속/삭제)
- [ ] N+1 문제와 해결 방법 (fetch join, @EntityGraph)
- [ ] JPQL vs QueryDSL 비교

### WebSocket
- [ ] WebSocket 핸드셰이크 과정 이해
- [ ] STOMP 프로토콜 이해
- [ ] 메시지 브로커 개념 (In-Memory vs RabbitMQ)

---

## 참고 자료

- [Spring Boot 공식 문서](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Data JPA 공식 문서](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Spring WebSocket 공식 문서](https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#websocket)
- [Baeldung - Spring 튜토리얼 (영문)](https://www.baeldung.com/)
- [김영한 - 자바 ORM 표준 JPA 프로그래밍](https://www.yes24.com/product/goods/19040233) ← JPA 학습 필독서

---

> 각 Phase 완료 후 해당 docs 파일이 자동 생성됩니다.
> 문서에는 구현 내용 요약, 핵심 학습 개념, NestJS 비교, 트러블슈팅 기록이 포함됩니다.
