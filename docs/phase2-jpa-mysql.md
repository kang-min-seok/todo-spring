# Phase 2 — JPA + MySQL 연동

---

## 1. 의존성 설치

`build.gradle.kts`에 두 줄을 추가하면 끝입니다.

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa") // JPA
    runtimeOnly("com.mysql:mysql-connector-j")                               // MySQL 드라이버
}
```

- `spring-boot-starter-data-jpa` — JPA + Hibernate + Spring Data JPA를 한 번에 설치
- `mysql-connector-j` — Java가 MySQL에 접속하기 위한 드라이버. `runtimeOnly`는 실행할 때만 필요하다는 의미

---

## 2. DB 연결 설정

`src/main/resources/application.properties`에 MySQL 연결 정보를 입력합니다.

```properties
# MySQL 접속 정보
spring.datasource.url=jdbc:mysql://localhost:3306/todo_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul
spring.datasource.username=todo_user
spring.datasource.password=todo1234
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA 설정
spring.jpa.hibernate.ddl-auto=update   # Entity 변경사항을 테이블에 자동 반영
spring.jpa.show-sql=true               # 실행되는 SQL을 콘솔에 출력 (학습용)
spring.jpa.properties.hibernate.format_sql=true
```

**`ddl-auto` 옵션:**

| 값 | 동작 |
|----|------|
| `update` | Entity 변경 시 테이블 자동 수정. 데이터는 유지. 개발용 |
| `create` | 시작할 때마다 테이블 삭제 후 재생성. 데이터 날아감 |
| `validate` | Entity와 테이블 구조가 다르면 에러. 운영용 |
| `none` | 아무것도 안 함 |

> MySQL 8.0은 기본 인증 방식이 바뀌어서 `allowPublicKeyRetrieval=true`를 안 붙이면 접속 오류가 납니다.

---

## 3. Entity 설정

DB 테이블과 매핑되는 클래스입니다. Phase 1의 순수 Java 객체에 어노테이션만 추가합니다.

```java
@Entity                  // 이 클래스가 DB 테이블과 매핑된다고 선언
@Table(name = "todos")   // 테이블 이름 지정 (생략하면 클래스명 사용)
@EntityListeners(AuditingEntityListener.class)  // @CreatedDate, @LastModifiedDate 동작에 필요
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA가 객체 생성 시 필요한 기본 생성자
@AllArgsConstructor                                  // @Builder가 사용하는 전체 생성자
public class Todo {

    @Id                                                  // PRIMARY KEY
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // AUTO_INCREMENT
    private Long id;

    @Column(nullable = false, length = 100)  // NOT NULL, VARCHAR(100)
    private String title;

    @Column(columnDefinition = "TEXT")       // TEXT 타입
    private String description;

    @Column(nullable = false)
    private boolean completed;

    @CreatedDate
    @Column(updatable = false)  // 최초 저장 후 변경 불가
    private LocalDateTime createdAt;

    @LastModifiedDate           // UPDATE 시 자동 갱신
    private LocalDateTime updatedAt;
}
```

**@Builder + JPA 조합 주의점:**

`@Builder`는 전체 필드 생성자를 필요로 하고, JPA는 기본 생성자를 필요로 합니다.
두 조건을 모두 만족하려면 `@NoArgsConstructor` + `@AllArgsConstructor`를 함께 써야 합니다.

---

## 4. Config 설정

### JpaConfig — Auditing 활성화

`@CreatedDate`, `@LastModifiedDate`가 동작하려면 별도로 활성화해야 합니다.

```java
@Configuration
@EnableJpaAuditing  // 이게 없으면 createdAt에 null이 들어감
public class JpaConfig {
}
```

### CorsConfig — 프론트엔드 연동 허용

프론트(localhost:5173)와 백엔드(localhost:8080)는 포트가 달라서 브라우저가 기본으로 요청을 차단합니다.
CORS 설정으로 허용해줍니다.

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

---

## 5. Repository 설정

인터페이스만 만들고 `JpaRepository`를 상속하면 기본 CRUD가 자동으로 생깁니다.
구현 클래스를 직접 작성할 필요가 없습니다.

```java
public interface TodoRepository extends JpaRepository<Todo, Long> {
    // 기본 제공: findAll(), findById(), save(), delete(), count() ...

    // 메서드 이름 규칙으로 쿼리 자동 생성
    // → SELECT * FROM todos WHERE UPPER(title) LIKE UPPER('%keyword%')
    Page<Todo> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);
}
```

**메서드 네이밍 규칙:**

| 키워드 | 의미 | SQL |
|--------|------|-----|
| `findBy필드명` | WHERE 조건 | `WHERE title = ?` |
| `Containing` | 포함 검색 | `LIKE '%?%'` |
| `IgnoreCase` | 대소문자 무시 | `UPPER(title)` |
| `OrderBy필드명Desc` | 내림차순 정렬 | `ORDER BY ? DESC` |

---

## 6. Service에서 JPA 사용

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 기본: 읽기 전용
public class TodoService {

    private final TodoRepository todoRepository;

    // 조회 — readOnly 트랜잭션 적용
    public Page<TodoResponse> findAll(Pageable pageable) {
        return todoRepository.findAll(pageable).map(TodoResponse::from);
    }

    // 생성 — 쓰기 트랜잭션으로 오버라이드
    @Transactional
    public TodoResponse create(CreateTodoRequest request) {
        Todo todo = Todo.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .completed(false)
                .build();
        return TodoResponse.from(todoRepository.save(todo));
    }

    // 수정 — save() 없이도 자동 UPDATE (Dirty Checking)
    @Transactional
    public TodoResponse update(Long id, UpdateTodoRequest request) {
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.TODO_NOT_FOUND));
        todo.update(request.getTitle(), request.getDescription());
        // save() 호출 안 해도 됨. 트랜잭션 끝날 때 변경 감지 후 자동 UPDATE
        return TodoResponse.from(todo);
    }
}
```

**`@Transactional(readOnly = true)` 패턴:**
- 클래스에 `readOnly = true`를 기본으로 붙임 → 모든 메서드가 읽기 전용
- 데이터를 변경하는 메서드에만 `@Transactional`을 별도로 붙여서 오버라이드

---

## 7. Controller에서 페이지네이션

```java
@GetMapping
public ResponseEntity<Page<TodoResponse>> findAll(
        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable) {
    return ResponseEntity.ok(todoService.findAll(pageable));
}
```

`Pageable`을 파라미터로 받으면 Spring이 쿼리 파라미터를 자동으로 변환해줍니다.

```
GET /api/todos?page=0&size=5&sort=createdAt,desc
                ↓
PageRequest.of(0, 5, Sort.by("createdAt").descending())
```

`@PageableDefault`는 쿼리 파라미터가 없을 때 적용할 기본값입니다.

---

## 8. 실행

```bash
# MySQL 컨테이너 먼저 실행
docker compose up -d

# 앱 실행
./gradlew bootRun
```

앱이 뜨면 Hibernate가 `show-sql=true` 설정에 따라 실행되는 SQL을 콘솔에 출력합니다.
`ddl-auto=update`로 설정했으므로 `todos` 테이블이 자동으로 생성됩니다.
