# Phase 1 — Spring Boot 기본 구조 & REST API

> 인메모리 저장소 기반 Todo CRUD REST API 구현

---

## 구현 파일 구조

```
src/main/java/com/study/todo_spring/
├── todo/
│   ├── controller/TodoController.java
│   ├── service/TodoService.java
│   ├── domain/Todo.java
│   └── dto/
│       ├── CreateTodoRequest.java
│       ├── UpdateTodoRequest.java
│       └── TodoResponse.java
└── global/
    └── exception/
        ├── ErrorCode.java
        ├── CustomException.java
        ├── ErrorResponse.java
        └── GlobalExceptionHandler.java
```

---

## API

| 메서드 | URL | 설명 | 응답 |
|--------|-----|------|------|
| GET | `/api/todos` | 전체 조회 | 200 |
| GET | `/api/todos/{id}` | 단건 조회 | 200 |
| POST | `/api/todos` | 생성 | 201 |
| PUT | `/api/todos/{id}` | 수정 | 200 |
| DELETE | `/api/todos/{id}` | 삭제 | 204 |
| PATCH | `/api/todos/{id}/complete` | 완료 처리 | 200 |

---

## 레이어별 역할과 핵심 어노테이션

### Controller — `@RestController`

HTTP 요청을 받아 Service에 위임하고 응답을 반환하는 레이어.

```java
@RestController           // @Controller + @ResponseBody. 반환값을 JSON으로 직렬화
@RequestMapping("/api/todos")  // 공통 URL prefix
@RequiredArgsConstructor  // final 필드를 인자로 받는 생성자 생성 → 생성자 DI
public class TodoController {
    private final TodoService todoService; // Spring이 주입
}
```

**라우팅 어노테이션:**

| 어노테이션 | HTTP 메서드 | NestJS 대응 |
|-----------|------------|------------|
| `@GetMapping` | GET | `@Get()` |
| `@PostMapping` | POST | `@Post()` |
| `@PutMapping` | PUT | `@Put()` |
| `@DeleteMapping` | DELETE | `@Delete()` |
| `@PatchMapping` | PATCH | `@Patch()` |

**파라미터 바인딩:**

```java
@GetMapping("/{id}")
public ResponseEntity<TodoResponse> findById(
    @PathVariable Long id          // URL 경로 변수. NestJS의 @Param('id')
) { ... }

@PostMapping
public ResponseEntity<TodoResponse> create(
    @Valid                         // DTO의 Bean Validation 어노테이션 실행
    @RequestBody CreateTodoRequest request  // 요청 바디 JSON → 객체. NestJS의 @Body()
) { ... }
```

**`ResponseEntity<T>`:**
HTTP 상태 코드와 응답 바디를 함께 제어하는 래퍼 클래스.
```java
ResponseEntity.ok(body)                        // 200
ResponseEntity.status(HttpStatus.CREATED).body(body)  // 201
ResponseEntity.noContent().build()             // 204 (바디 없음)
```

---

### Service — `@Service`

비즈니스 로직을 처리하는 레이어. 현재는 인메모리 `ArrayList`를 저장소로 사용한다.
Phase 2에서 JPA Repository로 교체 예정.

```java
@Service  // @Component의 특수화. ComponentScan이 Bean으로 자동 등록
public class TodoService {
    private final List<Todo> todoStore = new ArrayList<>();
    private final AtomicLong idSequence = new AtomicLong(1); // DB AUTO_INCREMENT 역할
}
```

**예외 처리 흐름:**

```java
// Service 내부의 private 헬퍼 메서드
private Todo getTodoOrThrow(Long id) {
    return todoStore.stream()
            .filter(todo -> todo.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new CustomException(ErrorCode.TODO_NOT_FOUND));
            //              ↑ Optional이 비어있으면 여기서 예외를 던진다
}
```

`findById()`, `update()`, `delete()`, `complete()` 등 ID가 필요한 모든 메서드가
`getTodoOrThrow()`를 호출한다. 예외가 던져지면 Spring이 `GlobalExceptionHandler`로 전달한다.

> **예외가 전달되는 원리:**
> `CustomException`은 `RuntimeException`을 상속한다.
> Spring MVC는 컨트롤러 레이어까지 올라온 미처리 예외를 `@ControllerAdvice`로 위임한다.
> Service → Controller → DispatcherServlet → GlobalExceptionHandler 순으로 예외가 전파된다.

---

### Domain — `Todo.java`

순수 Java 객체(POJO). 비즈니스 상태와 변경 로직을 담는다.
Phase 2에서 `@Entity`를 추가해 DB 테이블과 매핑한다.

```java
@Getter   // getTitle(), getId() 등 getter 자동 생성
@Builder  // Todo.builder().id(1L).title("제목").build() 빌더 패턴 자동 생성
public class Todo {
    private Long id;
    ...
    // 수정 로직을 Service가 아닌 도메인 객체 안에 캡슐화
    public void update(String title, String description) { ... }
    public void complete() { ... }
}
```

---

### DTO — 요청/응답 객체 분리

도메인 객체(Todo)를 직접 HTTP에 노출하지 않고 DTO를 통해 주고받는다.

```
요청 JSON → CreateTodoRequest (@Valid 검사) → Service → Todo (도메인) → TodoResponse → 응답 JSON
```

**Bean Validation 어노테이션 (jakarta.validation):**

| 어노테이션 | 설명 | NestJS 대응 |
|-----------|------|------------|
| `@NotBlank` | null, `""`, `" "` 거부 | `@IsNotEmpty()` |
| `@Size(max=100)` | 문자열 길이 제한 | `@MaxLength(100)` |
| `@NotNull` | null만 거부 | `@IsNotEmpty()` |
| `@Email` | 이메일 형식 검사 | `@IsEmail()` |
| `@Min` / `@Max` | 숫자 범위 | `@Min()` / `@Max()` |

`@Valid`가 없으면 어노테이션이 있어도 검사하지 않는다.

**`@NoArgsConstructor`가 필요한 이유:**
Jackson이 JSON → 객체 변환 시 기본 생성자를 먼저 호출한다.
`@Builder`만 있으면 기본 생성자가 없어 역직렬화 오류가 발생한다.

**`TodoResponse.from()` 정적 팩터리:**
```java
public static TodoResponse from(Todo todo) {
    return TodoResponse.builder()...build();
}
// 사용: TodoResponse.from(todo)
// 변환 로직이 한 곳에 모여 있어 도메인 변경 시 이 메서드만 수정하면 된다
```

---

### 전역 예외 처리 — `@RestControllerAdvice`

```java
@RestControllerAdvice  // 모든 @Controller에 적용되는 전역 예외 처리기
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)  // 이 예외 타입을 잡겠다
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(...);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)  // @Valid 실패
    public ResponseEntity<ErrorResponse> handleValidationException(...) { ... }

    @ExceptionHandler(Exception.class)  // 그 외 모든 예외 (최후 방어선)
    public ResponseEntity<ErrorResponse> handleException(...) { ... }
}
```

`ErrorCode` Enum으로 HTTP 상태코드 + 에러 메시지를 한 곳에서 관리:
```java
TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 Todo를 찾을 수 없습니다.")
```

에러 응답 형식:
```json
{ "status": 404, "code": "TODO_NOT_FOUND", "message": "해당 Todo를 찾을 수 없습니다." }
```

---

## DI(의존성 주입) 방식 비교

Spring에서 권장하는 **생성자 주입**을 사용했다.

```java
// ❌ 필드 주입 (지양) — 테스트 어렵고 final 불가
@Autowired
private TodoService todoService;

// ✅ 생성자 주입 (권장) — @RequiredArgsConstructor로 자동 생성
private final TodoService todoService;
```

생성자 주입의 장점: `final`로 불변성 보장, 테스트 시 Mock 주입 용이, 순환 의존성 컴파일 시 감지.

---

## 실행 및 테스트

```bash
./gradlew bootRun
```

```bash
# 생성
curl -X POST http://localhost:8080/api/todos \
  -H "Content-Type: application/json" \
  -d '{"title":"Spring 학습","description":"IoC/DI 이해하기"}'

# 전체 조회
curl http://localhost:8080/api/todos

# 유효성 검사 실패 확인 (빈 title)
curl -X POST http://localhost:8080/api/todos \
  -H "Content-Type: application/json" \
  -d '{"title":""}'
# → {"status":400,"code":"VALIDATION_ERROR","message":"title: 제목은 필수입니다."}
```

---

## 다음 단계 (Phase 2)

인메모리 `ArrayList` → JPA + MySQL 교체. 변경 포인트:
- `Todo.java` → `@Entity` 추가
- `TodoService` 내 `todoStore` → `TodoRepository` (JPA) 교체
- `build.gradle.kts` → `spring-data-jpa`, `mysql-connector` 의존성 추가
- `application.properties` → MySQL 연결 설정 추가
