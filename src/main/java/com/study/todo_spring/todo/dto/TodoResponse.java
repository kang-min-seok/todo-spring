package com.study.todo_spring.todo.dto;

import com.study.todo_spring.todo.domain.Todo;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * TodoResponse - Todo 응답 DTO
 *
 * [왜 도메인 객체를 바로 반환하지 않나?]
 * 1. 도메인 객체에는 외부에 노출하면 안 되는 필드가 있을 수 있다 (비밀번호 등)
 * 2. Phase 2(JPA)에서 @Entity가 되면 내부에 프록시 객체, 지연 로딩 등이 생겨
 *    직렬화(JSON 변환)가 의도치 않게 동작할 수 있다
 * 3. API 응답 형식을 도메인 변경과 독립적으로 관리할 수 있다
 *
 * [정적 팩터리 메서드 패턴]
 * from(Todo todo) 메서드로 도메인 → 응답 DTO 변환을 캡슐화한다.
 * NestJS에서 plainToInstance(TodoResponseDto, todo)와 유사한 목적이다.
 */
@Getter
@Builder
public class TodoResponse {

    private Long id;
    private String title;
    private String description;
    private boolean completed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 도메인 객체 → 응답 DTO 변환
     * Service에서 new TodoResponse(...)로 직접 생성하는 것보다
     * 이 메서드를 통해 변환하면 변환 로직이 한 곳에 모여 관리가 쉬워진다.
     */
    public static TodoResponse from(Todo todo) {
        return TodoResponse.builder()
                .id(todo.getId())
                .title(todo.getTitle())
                .description(todo.getDescription())
                .completed(todo.isCompleted())
                .createdAt(todo.getCreatedAt())
                .updatedAt(todo.getUpdatedAt())
                .build();
    }
}
