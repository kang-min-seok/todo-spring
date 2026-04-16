# Phase 2 — JPA & MySQL 실습 전 핵심 개념 정리

> 실습 전에 알아야 할 개념만 정리한 문서.
> 실습 완료 후 구현 내용은 `phase2-jpa-mysql.md`에 정리됩니다.

---

## JPA가 뭔가요?

**JPA(Java Persistence API)** = Java에서 DB를 다루는 표준 인터페이스(명세).
**Hibernate** = JPA 명세를 실제로 구현한 라이브러리.
**Spring Data JPA** = Hibernate 위에서 Repository 패턴을 편하게 쓸 수 있게 Spring이 감싼 것.

```
개발자가 쓰는 것   →  Spring Data JPA (JpaRepository)
그 아래           →  JPA (표준 인터페이스)
실제 동작         →  Hibernate (구현체)
최종 실행         →  JDBC → MySQL
```

TypeORM과 비교하면:

| | Spring | NestJS |
|--|--------|--------|
| 표준 인터페이스 | JPA | 없음 |
| 구현체 | Hibernate | TypeORM 자체 |
| 편의 레이어 | Spring Data JPA | TypeORM Repository |

---

## 핵심 개념 1 — Entity와 테이블 매핑

`@Entity`를 붙이면 JPA가 이 클래스를 DB 테이블과 연결합니다.
Phase 1의 순수 자바 객체 `Todo`에 어노테이션만 추가하면 됩니다.

```java
@Entity                  // JPA: 이 클래스는 DB 테이블과 매핑된다
@Table(name = "todos")   // 매핑할 테이블 이름 (생략하면 클래스명 = 테이블명)
public class Todo {

    @Id                                                 // PRIMARY KEY
    @GeneratedValue(strategy = GenerationType.IDENTITY) // AUTO_INCREMENT
    private Long id;

    @Column(nullable = false, length = 100)  // NOT NULL, VARCHAR(100)
    private String title;

    @Column(columnDefinition = "TEXT")       // TEXT 타입
    private String description;

    @Column(nullable = false)
    private boolean completed = false;

    @CreatedDate   // INSERT 시 자동으로 현재 시간 저장
    private LocalDateTime createdAt;

    @LastModifiedDate  // UPDATE 시 자동으로 현재 시간 갱신
    private LocalDateTime updatedAt;
}
```

NestJS TypeORM과 비교:

```typescript
// TypeORM
@Entity('todos')
export class Todo {
    @PrimaryGeneratedColumn()    id: number;
    @Column({ length: 100 })     title: string;
    @CreateDateColumn()          createdAt: Date;
    @UpdateDateColumn()          updatedAt: Date;
}
```

```java
// JPA
@Entity @Table(name = "todos")
public class Todo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 100)
    private String title;
    @CreatedDate  private LocalDateTime createdAt;
    @LastModifiedDate  private LocalDateTime updatedAt;
}
```

어노테이션 이름만 다를 뿐 구조가 거의 같습니다.

---

## 핵심 개념 2 — 영속성 컨텍스트 (JPA의 핵심)

JPA에서 가장 중요한 개념입니다. TypeORM에는 없는 개념이라 처음엔 낯섭니다.

영속성 컨텍스트 = **JPA가 Entity를 관리하는 메모리 공간 (1차 캐시)**.

```
[영속성 컨텍스트]
    ↕ 관리
[Entity 객체]  ←→  [DB]
```

**Entity의 4가지 상태:**

```java
// 1. 비영속 (Transient) — JPA가 모름, 그냥 자바 객체
Todo todo = new Todo();
todo.setTitle("공부하기");

// 2. 영속 (Managed) — JPA가 관리 중, 변경을 감지함
entityManager.persist(todo);  // 또는 repository.save(todo)

// 3. 준영속 (Detached) — JPA 관리에서 분리됨
entityManager.detach(todo);

// 4. 삭제 (Removed) — 삭제 예약됨
entityManager.remove(todo);
```

**영속 상태의 핵심 기능 — 변경 감지(Dirty Checking):**

```java
// TypeORM 방식 — 명시적으로 save() 호출 필요
todo.title = "변경된 제목";
await todoRepository.save(todo);  // 이걸 해야 DB에 반영됨

// JPA 방식 — @Transactional 안에서는 save() 없이도 자동 반영
@Transactional
public void update(Long id, String newTitle) {
    Todo todo = todoRepository.findById(id);
    todo.update(newTitle);  // 변경만 해도...
    // save() 없음! 트랜잭션 종료 시 JPA가 변경을 감지해서 UPDATE 쿼리 자동 실행
}
```

이것이 가능한 이유: 영속성 컨텍스트가 Entity의 초기 상태(스냅샷)를 기억하고,
트랜잭션이 끝날 때 현재 상태와 비교해서 달라진 부분만 UPDATE 쿼리를 날립니다.

---

## 핵심 개념 3 — JpaRepository

인터페이스를 만들고 `JpaRepository`를 상속하면
기본 CRUD 메서드를 Spring이 자동으로 구현해서 주입해줍니다.

```java
public interface TodoRepository extends JpaRepository<Todo, Long> {
    // 이것만 해도 아래 메서드들이 자동으로 생성됨
}

// 자동으로 제공되는 메서드들
todoRepository.findAll()           // SELECT * FROM todos
todoRepository.findById(id)        // SELECT * FROM todos WHERE id = ?  → Optional<Todo>
todoRepository.save(todo)          // INSERT 또는 UPDATE (id 유무로 판단)
todoRepository.delete(todo)        // DELETE
todoRepository.count()             // SELECT COUNT(*)
todoRepository.existsById(id)      // SELECT EXISTS(...)
```

TypeORM과 비교:

```typescript
// TypeORM — @InjectRepository()로 주입받아 사용
constructor(@InjectRepository(Todo) private todoRepo: Repository<Todo>) {}

this.todoRepo.find()
this.todoRepo.findOne({ where: { id } })
this.todoRepo.save(todo)
this.todoRepo.remove(todo)
```

```java
// JPA — 인터페이스 선언만 하면 Spring이 구현체 자동 생성 후 주입
@Repository
public interface TodoRepository extends JpaRepository<Todo, Long> {}
// Service에서: private final TodoRepository todoRepository;
```

---

## 핵심 개념 4 — 쿼리 작성 방법 2가지

### 방법 1: 메서드 네이밍 쿼리

메서드 이름을 규칙에 맞게 작성하면 JPA가 자동으로 쿼리를 생성합니다.

```java
public interface TodoRepository extends JpaRepository<Todo, Long> {
    // 메서드 이름 → 자동 생성 쿼리
    List<Todo> findByCompleted(boolean completed);
    // → SELECT * FROM todos WHERE completed = ?

    List<Todo> findByTitleContaining(String keyword);
    // → SELECT * FROM todos WHERE title LIKE '%keyword%'

    List<Todo> findByCompletedOrderByCreatedAtDesc(boolean completed);
    // → SELECT * FROM todos WHERE completed = ? ORDER BY created_at DESC
}
```

### 방법 2: JPQL `@Query`

복잡한 쿼리는 직접 작성합니다. SQL과 비슷하지만 **테이블명 대신 클래스명, 컬럼명 대신 필드명**을 씁니다.

```java
@Query("SELECT t FROM Todo t WHERE t.title LIKE %:keyword%")
List<Todo> searchByTitle(@Param("keyword") String keyword);

// SQL:  SELECT * FROM todos WHERE title LIKE '%keyword%'
// JPQL: SELECT t FROM Todo t WHERE t.title LIKE '%keyword%'
//                        ↑ 테이블명(todos) 아닌 클래스명(Todo)
```

---

## 핵심 개념 5 — @Transactional

DB 작업을 하나의 단위로 묶어서 **전부 성공하거나 전부 실패(rollback)** 하게 만드는 어노테이션.

```java
@Transactional
public void transfer(Long fromId, Long toId, int amount) {
    Account from = accountRepository.findById(fromId);
    Account to = accountRepository.findById(toId);

    from.withdraw(amount);  // 출금
    to.deposit(amount);     // 입금 ← 여기서 예외 발생 시

    // @Transactional: 출금도 같이 rollback됨 (데이터 정합성 보장)
    // 없으면: 출금만 되고 입금은 안 된 채로 DB에 남음
}
```

**Service에서의 실전 패턴:**

```java
@Service
@Transactional(readOnly = true)  // 클래스 레벨: 기본을 읽기 전용으로
public class TodoService {

    public List<TodoResponse> findAll() { ... }  // readOnly = true 적용

    @Transactional  // 쓰기 작업은 메서드에 별도 선언 (readOnly = false)
    public TodoResponse create(CreateTodoRequest request) { ... }

    @Transactional
    public TodoResponse update(Long id, UpdateTodoRequest request) { ... }
}
```

`readOnly = true`로 읽기 작업을 최적화하고, 쓰기 작업만 별도로 `@Transactional`을 붙이는 것이 권장 패턴입니다.

---

## 핵심 개념 6 — ddl-auto 설정

`application.properties`에서 JPA가 테이블을 어떻게 관리할지 설정합니다.

| 옵션 | 동작 | 사용 시점 |
|------|------|---------|
| `create` | 시작 시 테이블 삭제 후 재생성 | 초기 개발 |
| `create-drop` | 시작 시 생성, 종료 시 삭제 | 테스트 |
| `update` | Entity 변경사항만 반영 (데이터 유지) | 개발 중 |
| `validate` | Entity와 테이블 구조 일치 여부만 확인 | 운영 |
| `none` | 아무것도 하지 않음 | 운영 (Flyway 사용 시) |

> 운영 환경에서 `create`나 `update`를 쓰면 데이터가 날아갈 수 있습니다.
> 운영에서는 반드시 `validate` 또는 `none`을 사용합니다.

---

## 핵심 개념 7 — 페이지네이션 (Pageable)

JpaRepository는 페이지네이션을 기본으로 지원합니다.

```java
// Repository — 별도 구현 없이 Page<T> 반환 메서드 자동 지원
Page<Todo> findAll(Pageable pageable);

// Service — Pageable 객체 생성
Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
//                               ↑ 페이지번호(0부터)  ↑ 페이지 크기  ↑ 정렬

// Controller — 쿼리 파라미터로 받기
// GET /api/todos?page=0&size=10&sort=createdAt,desc
public ResponseEntity<Page<TodoResponse>> findAll(Pageable pageable) { ... }
```

`Page<T>` 응답에는 데이터와 함께 페이지 정보가 포함됩니다:

```json
{
  "content": [ ... ],       // 실제 데이터 목록
  "totalElements": 100,     // 전체 데이터 수
  "totalPages": 10,         // 전체 페이지 수
  "number": 0,              // 현재 페이지 번호
  "size": 10                // 페이지 크기
}
```

---

## 실습 전 의존성 추가 예고

Phase 2 실습에서 `build.gradle.kts`에 아래 의존성이 추가됩니다.

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
runtimeOnly("com.mysql:mysql-connector-j")
```

그리고 `application.properties`에 MySQL 연결 설정과 JPA 설정이 추가됩니다.

---

## 개념 요약

| 개념 | 한 줄 요약 |
|------|-----------|
| JPA / Hibernate | DB 작업을 Java 객체로 다루는 ORM. Hibernate가 JPA의 실제 구현체 |
| `@Entity` | 이 클래스를 DB 테이블과 매핑 |
| 영속성 컨텍스트 | JPA가 Entity를 관리하는 메모리 공간. 변경 감지로 자동 UPDATE |
| `JpaRepository` | CRUD 메서드를 자동으로 제공하는 인터페이스 |
| 메서드 네이밍 쿼리 | 메서드 이름 규칙으로 자동 쿼리 생성 |
| `@Transactional` | DB 작업을 하나의 단위로 묶어 실패 시 전체 롤백 |
| `ddl-auto` | JPA가 테이블을 자동 생성/수정할지 결정하는 설정 |
| `Pageable` | 페이지네이션을 편리하게 처리하는 Spring 기능 |
