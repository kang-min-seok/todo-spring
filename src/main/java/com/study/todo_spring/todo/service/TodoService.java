package com.study.todo_spring.todo.service;

import com.study.todo_spring.global.exception.CustomException;
import com.study.todo_spring.global.exception.ErrorCode;
import com.study.todo_spring.todo.domain.Todo;
import com.study.todo_spring.todo.dto.CreateTodoRequest;
import com.study.todo_spring.todo.dto.TodoResponse;
import com.study.todo_spring.todo.dto.UpdateTodoRequest;
import com.study.todo_spring.todo.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TodoService - 할 일 비즈니스 로직 (Phase 2: JPA 기반)
 *
 * Phase 1과 달라진 점:
 *   1. ArrayList(todoStore) + AtomicLong(idSequence) → TodoRepository(JPA) 로 교체
 *   2. @Transactional 추가 — 읽기는 readOnly = true, 쓰기는 일반 트랜잭션
 *   3. update() 메서드에서 save() 호출 불필요 — Dirty Checking이 자동 처리
 *   4. create() 메서드에서 id/createdAt/updatedAt 직접 설정 불필요 — DB와 Auditing이 자동 처리
 *
 * [@Transactional(readOnly = true) 클래스 레벨 선언]
 * 이 서비스의 모든 메서드에 readOnly 트랜잭션을 기본으로 적용한다.
 * readOnly = true 이면:
 *   - Hibernate가 Dirty Checking(변경 감지)을 수행하지 않아 성능이 소폭 향상된다.
 *   - 실수로 조회 메서드에서 데이터를 변경하는 버그를 방지한다.
 * 쓰기 작업이 필요한 메서드에는 @Transactional을 별도로 선언해서 오버라이드한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    /**
     * Phase 1의 ArrayList + AtomicLong이 TodoRepository 하나로 교체됐다.
     * JPA가 id 발급, 저장, 조회, 삭제를 모두 처리한다.
     */
    private final TodoRepository todoRepository;

    // ── 전체 조회 (페이지네이션) ──────────────────────────────────────────────

    /**
     * Pageable : 페이지 번호, 크기, 정렬 정보를 담는 객체
     * Controller에서 쿼리 파라미터(?page=0&size=10&sort=createdAt,desc)를 자동으로 바인딩해준다.
     *
     * Page<T>.map() : Page 안의 각 Todo를 TodoResponse로 변환한 새 Page를 반환한다.
     * Stream의 .map()과 동일한 개념이다.
     */
    public Page<TodoResponse> findAll(Pageable pageable) {
        return todoRepository.findAll(pageable)
                .map(TodoResponse::from);
    }

    // ── 단건 조회 ─────────────────────────────────────────────────────────────

    public TodoResponse findById(Long id) {
        Todo todo = getTodoOrThrow(id);
        return TodoResponse.from(todo);
    }

    // ── 생성 ──────────────────────────────────────────────────────────────────

    /**
     * @Transactional : 이 메서드는 쓰기 작업이므로 클래스 레벨의 readOnly를 오버라이드한다.
     *
     * Phase 1과 달라진 점:
     *   - id를 직접 발급하지 않는다 → DB AUTO_INCREMENT가 처리
     *   - createdAt, updatedAt을 설정하지 않는다 → @CreatedDate, @LastModifiedDate가 처리
     *   - todoStore.add() 대신 todoRepository.save() 호출
     *     save()는 id가 없으면 INSERT, 있으면 UPDATE를 실행한다
     */
    @Transactional
    public TodoResponse create(CreateTodoRequest request) {
        Todo todo = Todo.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .completed(false)
                .build();

        Todo saved = todoRepository.save(todo); // INSERT + id/createdAt/updatedAt 자동 설정
        return TodoResponse.from(saved);
    }

    // ── 수정 ──────────────────────────────────────────────────────────────────

    /**
     * [Dirty Checking(변경 감지) 동작 방식]
     * 1. findById()로 영속 상태의 Todo를 가져온다
     * 2. todo.update()로 필드를 변경한다
     * 3. save()를 호출하지 않는다
     * 4. 트랜잭션이 종료될 때 Hibernate가 초기 상태(스냅샷)와 현재 상태를 비교한다
     * 5. 변경된 필드만 UPDATE 쿼리로 자동 실행한다
     *
     * TypeORM은 변경 후 명시적으로 repository.save(entity)를 호출해야 하지만,
     * JPA는 영속 상태의 객체는 트랜잭션 안에서 변경만 해도 자동으로 DB에 반영된다.
     */
    @Transactional
    public TodoResponse update(Long id, UpdateTodoRequest request) {
        Todo todo = getTodoOrThrow(id);
        todo.update(request.getTitle(), request.getDescription()); // 변경만 하면 됨
        // save() 불필요 — Dirty Checking이 트랜잭션 종료 시 UPDATE 쿼리 자동 실행
        return TodoResponse.from(todo);
    }

    // ── 삭제 ──────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long id) {
        Todo todo = getTodoOrThrow(id);
        todoRepository.delete(todo);
    }

    // ── 완료 처리 ─────────────────────────────────────────────────────────────

    @Transactional
    public TodoResponse complete(Long id) {
        Todo todo = getTodoOrThrow(id);
        todo.complete(); // Dirty Checking으로 자동 UPDATE
        return TodoResponse.from(todo);
    }

    // ── 제목 검색 (페이지네이션) ──────────────────────────────────────────────

    /**
     * TodoRepository의 메서드 네이밍 쿼리를 사용한다.
     * findByTitleContainingIgnoreCase → WHERE UPPER(title) LIKE UPPER('%keyword%')
     */
    public Page<TodoResponse> search(String keyword, Pageable pageable) {
        return todoRepository.findByTitleContainingIgnoreCase(keyword, pageable)
                .map(TodoResponse::from);
    }

    // ── private 헬퍼 ──────────────────────────────────────────────────────────

    private Todo getTodoOrThrow(Long id) {
        // findById()는 Optional<Todo>를 반환한다
        // orElseThrow()는 Optional이 비어있으면 예외를 던진다
        return todoRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.TODO_NOT_FOUND));
    }
}
