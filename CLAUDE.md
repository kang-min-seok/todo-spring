# Todo Spring - 프로젝트 가이드

## 프로젝트 개요

NestJS/TypeScript 백엔드 경험이 있는 프론트엔드 개발자 지망생이 Spring Boot를 학습하기 위한 Todo 서비스 백엔드 프로젝트.
Spring의 핵심 개념(IoC, DI, AOP)을 NestJS와 비교하며 학습하고, JPA/MySQL 연동, WebSocket 실시간 통신, Docker Compose CI/CD까지 완성하는 것이 목표.

## 기술 스택

| 분류 | 기술 |
|------|------|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 4.x |
| ORM | Spring Data JPA (Hibernate) |
| DB | MySQL 8.x |
| 실시간 | Spring WebSocket (STOMP) |
| 빌드 | Gradle (Kotlin DSL) |
| 컨테이너 | Docker, Docker Compose |
| CI/CD | GitHub Actions |

---

## 코드 작성 규칙

### 1. 주석 필수 작성

- 모든 클래스 상단에 **역할 설명 + NestJS 대응 개념** 주석을 달 것
- 중요한 어노테이션(@Entity, @Transactional, @Repository 등)에는 한 줄 설명 추가
- 비즈니스 로직의 핵심 흐름에 인라인 주석 작성

```java
/**
 * TodoService - 할 일 관련 비즈니스 로직 처리
 *
 * [NestJS 비교]
 * NestJS의 @Injectable() Service와 동일한 역할.
 * Spring에서는 @Service 어노테이션을 붙이면 Spring IoC 컨테이너가
 * 이 클래스를 Bean으로 등록하고 의존성 주입(DI)을 관리한다.
 *
 * NestJS: providers 배열에 등록 → Module이 DI 관리
 * Spring: @Service/@Component 어노테이션 → ApplicationContext가 DI 관리
 */
```

### 2. NestJS 비교 설명 포함

코드 구현 시 NestJS와 다른 핵심 개념이 등장하면 반드시 비교 주석을 달 것:

| Spring 개념 | NestJS 대응 | 설명 |
|------------|------------|------|
| `@RestController` | `@Controller` | HTTP 요청 처리 클래스 |
| `@Service` | `@Injectable()` Service | 비즈니스 로직 레이어 |
| `@Repository` | `@Injectable()` Repository | 데이터 접근 레이어 |
| `@Entity` | TypeORM `@Entity()` | DB 테이블 매핑 클래스 |
| `@Transactional` | TypeORM QueryRunner | 트랜잭션 처리 |
| `@Autowired` / 생성자 주입 | Module의 DI | 의존성 주입 |
| `application.properties` | `.env` / config module | 환경 설정 |
| `@ControllerAdvice` | ExceptionFilter | 전역 예외 처리 |
| `@Valid` + `BindingResult` | class-validator + Pipe | 요청 유효성 검사 |
| Spring Security | Passport.js / Guards | 인증/인가 |

### 3. 문서 자동 작성

- 각 기능 구현 완료 후 `docs/` 폴더에 구현 정리 문서 작성
- 문서 형식: 구현 내용 요약 + 핵심 학습 개념 + NestJS 비교 + 코드 예시

---

## 레이어드 아키텍처

```
Controller (프레젠테이션 레이어)   ← HTTP/WebSocket 요청/응답
    ↕
Service (비즈니스 레이어)          ← 비즈니스 로직, @Transactional
    ↕
Repository (데이터 접근 레이어)    ← JPA, DB 쿼리
    ↕
Entity/Domain (도메인 레이어)      ← DB 테이블 매핑
```

NestJS의 Module → Controller → Service → Repository 패턴과 동일한 구조.
Spring에서는 Module 대신 패키지 단위로 레이어를 분리함.

---

## 패키지 구조 (목표)

```
src/main/java/com/study/todo_spring/
├── TodoSpringApplication.java       # 진입점 (NestJS의 main.ts)
├── todo/                            # Todo 도메인
│   ├── controller/
│   │   └── TodoController.java
│   ├── service/
│   │   └── TodoService.java
│   ├── repository/
│   │   └── TodoRepository.java
│   ├── entity/
│   │   └── Todo.java
│   └── dto/
│       ├── CreateTodoRequest.java
│       ├── UpdateTodoRequest.java
│       └── TodoResponse.java
├── websocket/                       # WebSocket 실시간 통신
│   ├── config/
│   │   └── WebSocketConfig.java
│   └── handler/
│       └── TodoWebSocketHandler.java
├── global/                          # 전역 설정
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   └── CustomException.java
│   └── config/
│       └── JpaConfig.java
```

---

## 구현 순서 (학습 로드맵)

1. **Phase 1** - Spring Boot 기본 구조 + REST API (docs/phase1-spring-basics.md)
2. **Phase 2** - JPA + MySQL 연동 (docs/phase2-jpa-mysql.md)
3. **Phase 3** - WebSocket 실시간 통신 (docs/phase3-websocket.md)
4. **Phase 4** - Docker Compose 컨테이너화 (docs/phase4-docker.md)
5. **Phase 5** - GitHub Actions CI/CD (docs/phase5-cicd.md)

---

## 개발 환경 실행

```bash
# 로컬 실행 (MySQL 필요)
./gradlew bootRun

# Docker Compose로 전체 실행
docker compose up -d

# 테스트
./gradlew test
```

---

## 중요 원칙

- 학습 목적이므로 코드보다 **이해**가 우선. 주석과 문서를 충실히 작성할 것
- NestJS와 비교하는 관점을 항상 유지할 것
- 각 어노테이션이 "왜" 필요한지 주석으로 설명할 것
- 구현 완료 후 반드시 해당 Phase 문서를 업데이트할 것
