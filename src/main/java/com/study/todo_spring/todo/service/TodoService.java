package com.study.todo_spring.todo.service;

import com.study.todo_spring.global.exception.CustomException;
import com.study.todo_spring.global.exception.ErrorCode;
import com.study.todo_spring.todo.domain.Todo;
import com.study.todo_spring.todo.dto.CreateTodoRequest;
import com.study.todo_spring.todo.dto.TodoResponse;
import com.study.todo_spring.todo.dto.UpdateTodoRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * TodoService - 할 일 관련 비즈니스 로직 처리
 *
 * [@Service 란?]
 * @Component의 특수화된 형태다. @Component와 기능은 동일하지만
 * "이 클래스는 비즈니스 로직을 담당한다"는 의미를 명확히 표현하기 위해 사용한다.
 * Spring의 @ComponentScan이 이 어노테이션을 감지해 Bean으로 자동 등록한다.
 *
 * [NestJS 비교]
 * @Injectable() 데코레이터를 붙인 NestJS Service와 동일한 역할이다.
 * 차이점: NestJS는 Module의 providers 배열에 명시적으로 등록해야 하지만,
 *        Spring은 @Service만 붙이면 @ComponentScan이 자동으로 찾아서 등록한다.
 *
 * [현재 구현: 인메모리 저장소]
 * 실제 DB 없이 Java의 ArrayList에 데이터를 저장한다.
 * 서버를 재시작하면 데이터가 사라진다.
 * Phase 2에서 JPA Repository로 교체할 예정이다.
 *
 * [DI(의존성 주입) 방식]
 * 이 서비스는 현재 Repository에 의존하지 않으므로 DI 예시가 없지만,
 * Controller에서 이 Service를 주입받는 방식을 TodoController에서 확인할 수 있다.
 */
@Service
public class TodoService {

    /**
     * 인메모리 저장소 (Phase 2에서 TodoRepository로 교체)
     * ArrayList는 멀티스레드 환경에서 안전하지 않지만, 학습 목적으로 사용한다.
     * 실제 서비스에서는 DB를 사용하므로 이 문제가 발생하지 않는다.
     */
    private final List<Todo> todoStore = new ArrayList<>();

    /**
     * AtomicLong : 멀티스레드 환경에서 안전하게 숫자를 증가시키는 클래스
     * 일반 long 변수는 동시 접근 시 같은 ID가 발급될 수 있다.
     * DB의 AUTO_INCREMENT와 동일한 역할이다.
     */
    private final AtomicLong idSequence = new AtomicLong(1);

    // ──────────────────────────────────────────────────────────────────────────
    // 전체 조회
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 모든 Todo를 조회한다.
     *
     * [Stream API]
     * Java 8+ 의 함수형 스타일 컬렉션 처리 API다.
     * todoStore.stream()          : List를 스트림으로 변환
     * .map(TodoResponse::from)    : 각 Todo를 TodoResponse로 변환 (메서드 참조)
     * .collect(Collectors.toList()): 다시 List로 수집
     *
     * NestJS의 todos.map(todo => TodoResponseDto.from(todo)) 와 완전히 동일하다.
     */
    public List<TodoResponse> findAll() {
        return todoStore.stream()
                .map(TodoResponse::from)
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 단건 조회
    // ──────────────────────────────────────────────────────────────────────────

    public TodoResponse findById(Long id) {
        // private 헬퍼 메서드로 찾기 + 없으면 예외 로직을 분리했다
        Todo todo = getTodoOrThrow(id);
        return TodoResponse.from(todo);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 생성
    // ──────────────────────────────────────────────────────────────────────────

    public TodoResponse create(CreateTodoRequest request) {
        Todo todo = Todo.builder()
                .id(idSequence.getAndIncrement())  // ID 자동 발급 후 1 증가
                .title(request.getTitle())
                .description(request.getDescription())
                .completed(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        todoStore.add(todo);
        return TodoResponse.from(todo);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 수정
    // ──────────────────────────────────────────────────────────────────────────

    public TodoResponse update(Long id, UpdateTodoRequest request) {
        Todo todo = getTodoOrThrow(id);
        // 도메인 객체의 메서드를 통해 수정한다 (캡슐화)
        todo.update(request.getTitle(), request.getDescription());
        return TodoResponse.from(todo);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 삭제
    // ──────────────────────────────────────────────────────────────────────────

    public void delete(Long id) {
        Todo todo = getTodoOrThrow(id);
        todoStore.remove(todo);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 완료 처리
    // ──────────────────────────────────────────────────────────────────────────

    public TodoResponse complete(Long id) {
        Todo todo = getTodoOrThrow(id);
        todo.complete();
        return TodoResponse.from(todo);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // private 헬퍼 메서드
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * ID로 Todo를 찾고, 없으면 CustomException을 던진다.
     * 여러 메서드에서 반복되는 "찾기 + 예외" 로직을 하나로 모은 것이다.
     *
     * [Optional.orElseThrow()]
     * Java의 Optional은 null 안전성을 위한 래퍼 클래스다.
     * stream().findFirst()는 Optional<Todo>를 반환하고,
     * .orElseThrow()는 값이 없으면 예외를 던진다.
     * NestJS의 if (!todo) throw new NotFoundException() 패턴과 동일하다.
     */
    private Todo getTodoOrThrow(Long id) {
        return todoStore.stream()
                .filter(todo -> todo.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.TODO_NOT_FOUND));
    }
}
