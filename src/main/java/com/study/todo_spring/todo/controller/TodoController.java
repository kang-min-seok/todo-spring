package com.study.todo_spring.todo.controller;

import com.study.todo_spring.todo.dto.CreateTodoRequest;
import com.study.todo_spring.todo.dto.TodoResponse;
import com.study.todo_spring.todo.dto.UpdateTodoRequest;
import com.study.todo_spring.todo.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * TodoController - Todo CRUD HTTP 엔드포인트 (Phase 2: 페이지네이션 + 검색 추가)
 *
 * Phase 1과 달라진 점:
 *   - findAll() : List → Page 반환 (페이지네이션 지원)
 *   - search()  : 제목 키워드 검색 엔드포인트 추가
 */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    // ── GET /api/todos — 전체 조회 (페이지네이션) ─────────────────────────────

    /**
     * Pageable : Spring이 쿼리 파라미터를 자동으로 Pageable 객체로 변환해준다.
     *   ?page=0        → 0번 페이지 (0부터 시작)
     *   ?size=10       → 페이지당 10개
     *   ?sort=createdAt,desc → createdAt 기준 내림차순 정렬
     *   파라미터 없으면 → @PageableDefault 기본값 적용
     *
     * @PageableDefault : 쿼리 파라미터가 없을 때 적용할 기본값 설정
     *   size = 10      → 페이지당 10개
     *   sort = "createdAt" → createdAt 기준 정렬
     *   direction = DESC   → 내림차순 (최신순)
     *
     * [NestJS 비교]
     * NestJS에서 직접 @Query('page') page: number 로 받아 처리하는 것과 달리,
     * Spring은 Pageable 하나로 page/size/sort를 한 번에 받아 Repository까지 전달한다.
     */
    @GetMapping
    public ResponseEntity<Page<TodoResponse>> findAll(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(todoService.findAll(pageable));
    }

    // ── GET /api/todos/{id} — 단건 조회 ──────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<TodoResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(todoService.findById(id));
    }

    // ── GET /api/todos/search?keyword=xxx — 제목 검색 ─────────────────────────

    /**
     * @RequestParam : URL 쿼리 파라미터를 메서드 인자로 바인딩
     *   ?keyword=공부  → keyword = "공부"
     * NestJS의 @Query('keyword') 와 동일하다.
     *
     * 예시: GET /api/todos/search?keyword=Spring&page=0&size=5
     */
    @GetMapping("/search")
    public ResponseEntity<Page<TodoResponse>> search(
            @RequestParam String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(todoService.search(keyword, pageable));
    }

    // ── POST /api/todos — 생성 ────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<TodoResponse> create(@Valid @RequestBody CreateTodoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(todoService.create(request));
    }

    // ── PUT /api/todos/{id} — 수정 ────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<TodoResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTodoRequest request) {
        return ResponseEntity.ok(todoService.update(id, request));
    }

    // ── DELETE /api/todos/{id} — 삭제 ────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        todoService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── PATCH /api/todos/{id}/complete — 완료 처리 ───────────────────────────

    @PatchMapping("/{id}/complete")
    public ResponseEntity<TodoResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok(todoService.complete(id));
    }
}
