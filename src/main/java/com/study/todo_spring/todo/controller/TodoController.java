package com.study.todo_spring.todo.controller;

import com.study.todo_spring.todo.dto.CreateTodoRequest;
import com.study.todo_spring.todo.dto.TodoResponse;
import com.study.todo_spring.todo.dto.UpdateTodoRequest;
import com.study.todo_spring.todo.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TodoController - Todo CRUD HTTP 엔드포인트
 *
 * [@RestController 란?]
 * @Controller + @ResponseBody 의 조합이다.
 *   - @Controller    : DispatcherServlet이 요청을 위임할 클래스로 등록
 *   - @ResponseBody  : 메서드 반환값을 JSON으로 직렬화해서 응답 바디에 담는다
 *
 * NestJS의 @Controller() + 각 메서드에 @Get()/@Post() 붙이는 것과 동일하다.
 *
 * [@RequestMapping("/api/todos")]
 * 이 Controller의 모든 메서드에 공통으로 적용되는 기본 URL 경로다.
 * NestJS의 @Controller('api/todos') 와 동일하다.
 *
 * [생성자 주입 - DI(의존성 주입)]
 * Spring DI의 핵심: Controller가 직접 Service를 생성하지 않는다.
 * Spring IoC 컨테이너가 TodoService Bean을 생성해서 주입해준다.
 *
 * 세 가지 DI 방식 비교:
 *
 * 1. 필드 주입 (지양)
 *    @Autowired
 *    private TodoService todoService;
 *    → 테스트하기 어렵고, 순환 의존성 감지가 어렵다.
 *
 * 2. 세터 주입 (선택적 의존성에만 사용)
 *    @Autowired
 *    public void setTodoService(TodoService todoService) { ... }
 *
 * 3. 생성자 주입 (권장) ← 현재 사용 방식
 *    @RequiredArgsConstructor : final 필드를 인자로 받는 생성자를 Lombok이 자동 생성
 *    private final TodoService todoService;
 *    → 불변성 보장, 테스트 용이, 순환 의존성 컴파일 시 감지
 *
 * [NestJS 비교]
 * NestJS도 constructor(private readonly todoService: TodoService) 형태로
 * 생성자 주입을 권장한다. 완전히 같은 패턴이다.
 */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor  // private final 필드를 주입받는 생성자 자동 생성
public class TodoController {

    /**
     * final: 한 번 주입되면 변경 불가 (불변성 보장)
     * Spring이 이 필드를 자동으로 채워준다 (IoC)
     */
    private final TodoService todoService;

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/todos — 전체 조회
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * @GetMapping : HTTP GET 요청 처리
     * ResponseEntity<T> : HTTP 상태 코드 + 응답 바디를 함께 제어
     *                     NestJS의 @Res() res: Response 객체 또는 직접 return 값과 동일
     */
    @GetMapping
    public ResponseEntity<List<TodoResponse>> findAll() {
        return ResponseEntity.ok(todoService.findAll());  // 200 OK
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/todos/{id} — 단건 조회
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * @PathVariable : URL 경로의 {id} 값을 메서드 파라미터로 바인딩
     * NestJS의 @Param('id') 와 동일하다.
     * Spring은 타입 변환을 자동으로 한다 (String "1" → Long 1L)
     */
    @GetMapping("/{id}")
    public ResponseEntity<TodoResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(todoService.findById(id));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/todos — 생성
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * @RequestBody : HTTP 요청 바디의 JSON을 Java 객체로 역직렬화 (Jackson이 처리)
     *               NestJS의 @Body() 데코레이터와 동일하다.
     *
     * @Valid : @RequestBody로 받은 객체에 Bean Validation 어노테이션을 실행한다.
     *         검사 실패 시 MethodArgumentNotValidException 발생
     *         → GlobalExceptionHandler에서 400 응답으로 처리됨
     *         NestJS의 ValidationPipe가 하는 역할과 동일하다.
     */
    @PostMapping
    public ResponseEntity<TodoResponse> create(@Valid @RequestBody CreateTodoRequest request) {
        // 201 Created : 리소스 생성 성공을 나타내는 HTTP 상태 코드
        return ResponseEntity.status(HttpStatus.CREATED).body(todoService.create(request));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PUT /api/todos/{id} — 전체 수정
    // ──────────────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<TodoResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTodoRequest request) {
        return ResponseEntity.ok(todoService.update(id, request));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELETE /api/todos/{id} — 삭제
    // ──────────────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        todoService.delete(id);
        // 204 No Content : 성공했지만 응답 바디가 없음
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PATCH /api/todos/{id}/complete — 완료 처리
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * PATCH : 리소스의 일부만 변경할 때 사용하는 HTTP 메서드
     * PUT이 전체 교체라면, PATCH는 부분 수정이다.
     * 여기서는 completed 필드만 true로 바꾸므로 PATCH가 적절하다.
     */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<TodoResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok(todoService.complete(id));
    }
}
