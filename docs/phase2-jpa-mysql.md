# Phase 2 — Spring Data JPA & MySQL 연동 (완료)

> 인메모리 저장소 → MySQL 영속화. 페이지네이션 + 제목 검색 기능 추가.

---

## 변경된 파일 / 추가된 파일

| 파일 | 변경 내용 |
|------|---------|
| `build.gradle.kts` | `spring-data-jpa`, `mysql-connector-j` 의존성 추가 |
| `application.properties` | MySQL 연결 설정, JPA 설정 추가 |
| `todo/domain/Todo.java` | POJO → JPA Entity 변환 |
| `global/config/JpaConfig.java` | `@EnableJpaAuditing` 설정 클래스 추가 |
| `todo/repository/TodoRepository.java` | JpaRepository 상속 인터페이스 신규 추가 |
| `todo/service/TodoService.java` | ArrayList → JpaRepository 교체, `@Transactional` 추가 |
| `todo/controller/TodoController.java` | `Page` 반환 + `/search` 엔드포인트 추가 |
| `docker-compose.yml` | MySQL 컨테이너 설정 추가 |

---

## API 변경사항

| 메서드 | URL | 변경 내용 |
|--------|-----|---------|
| GET | `/api/todos` | 응답 타입 `List` → `Page` (페이지네이션 지원) |
| GET | `/api/todos/search?keyword=xxx` | 신규 추가 — 제목 키워드 검색 |
| 나머지 | 동일 | 동작은 같고 데이터가 MySQL에 영속화됨 |

**페이지네이션 쿼리 파라미터:**
```
GET /api/todos?page=0&size=10&sort=createdAt,desc
```

**Page 응답 형식:**
```json
{
  "content": [ ... ],
  "totalElements": 42,
  "totalPages": 5,
  "number": 0,
  "size": 10,
  "first": true,
  "last": false
}
```

---

## 핵심 구현 설명

### 1. Entity 변환 — `Todo.java`

Phase 1 대비 추가된 어노테이션:

```java
@Entity                                          // DB 테이블 매핑
@Table(name = "todos")                           // 테이블 이름 지정
@EntityListeners(AuditingEntityListener.class)   // @CreatedDate, @LastModifiedDate 활성화
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 필수 기본 생성자
@AllArgsConstructor                              // @Builder가 사용하는 전체 생성자
```

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)  // AUTO_INCREMENT
private Long id;

@CreatedDate
@Column(updatable = false)    // INSERT 시만 설정, 이후 변경 불가
private LocalDateTime createdAt;

@LastModifiedDate             // UPDATE 시 자동 갱신
private LocalDateTime updatedAt;
```

`update()`, `complete()` 비즈니스 메서드는 Phase 1 코드 그대로 유지됐다.
`updatedAt`은 `@LastModifiedDate`가 자동으로 갱신하므로 메서드에서 직접 설정하지 않는다.

**`@Builder` + JPA Entity 조합 시 주의점:**

`@Builder`는 전체 필드를 인자로 받는 생성자가 필요하고,
JPA는 인자 없는 기본 생성자가 필요하다.
두 조건을 모두 충족하려면 `@NoArgsConstructor` + `@AllArgsConstructor`를 함께 선언해야 한다.

---

### 2. Repository — `TodoRepository.java`

```java
public interface TodoRepository extends JpaRepository<Todo, Long> {
    Page<Todo> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);
}
```

구현 클래스 없이 인터페이스만 선언했다.
Spring Data JPA가 런타임에 구현체를 자동 생성해서 Bean으로 등록한다.

`findByTitleContainingIgnoreCase`는 메서드 이름만으로 아래 쿼리가 자동 생성된다:
```sql
SELECT * FROM todos
WHERE UPPER(title) LIKE UPPER('%keyword%')
ORDER BY created_at DESC
LIMIT 10 OFFSET 0
```

---

### 3. Service — `@Transactional` 패턴

```java
@Transactional(readOnly = true)   // 클래스 기본: 모든 메서드에 읽기 전용 적용
public class TodoService {

    public Page<TodoResponse> findAll(Pageable pageable) { ... }  // readOnly = true

    @Transactional   // 쓰기 메서드만 오버라이드 (readOnly = false)
    public TodoResponse create(...) { ... }

    @Transactional
    public TodoResponse update(...) { ... }  // save() 없어도 Dirty Checking으로 자동 UPDATE
}
```

**Dirty Checking(변경 감지) 동작:**
```java
@Transactional
public TodoResponse update(Long id, UpdateTodoRequest request) {
    Todo todo = todoRepository.findById(id)...  // 영속 상태로 가져옴
    todo.update(request.getTitle(), ...);        // 필드 변경
    // save() 없음 — 트랜잭션 종료 시 Hibernate가 변경 감지 후 UPDATE 자동 실행
    return TodoResponse.from(todo);
}
```

---

### 4. Controller — Pageable 바인딩

```java
@GetMapping
public ResponseEntity<Page<TodoResponse>> findAll(
        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable) {
    return ResponseEntity.ok(todoService.findAll(pageable));
}
```

Spring이 `?page=0&size=10&sort=createdAt,desc` 쿼리 파라미터를
`Pageable` 객체로 자동 변환해서 주입해준다.
파라미터가 없으면 `@PageableDefault` 기본값이 적용된다.

---

## 실행 방법

```bash
# 1. MySQL 컨테이너 실행
docker compose up -d

# 2. MySQL이 준비될 때까지 대기 (healthcheck 통과 확인)
docker compose ps

# 3. Spring Boot 앱 실행
./gradlew bootRun
```

```bash
# 테스트
# 생성
curl -X POST http://localhost:8080/api/todos \
  -H "Content-Type: application/json" \
  -d '{"title":"JPA 공부","description":"영속성 컨텍스트 이해하기"}'

# 페이지네이션 조회
curl "http://localhost:8080/api/todos?page=0&size=5&sort=createdAt,desc"

# 제목 검색
curl "http://localhost:8080/api/todos/search?keyword=JPA"
```

---

## 다음 단계 (Phase 3)

WebSocket + STOMP로 Todo 변경 시 실시간 브로드캐스트 구현.
→ `docs/phase3-websocket.md`
