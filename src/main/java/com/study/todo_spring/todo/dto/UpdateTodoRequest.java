package com.study.todo_spring.todo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * UpdateTodoRequest - Todo 수정 요청 DTO
 *
 * 수정 요청도 별도 DTO로 분리하는 이유:
 * - 생성과 수정에서 허용되는 필드가 다를 수 있다 (예: id, createdAt은 수정 불가)
 * - 수정 시에만 적용되는 유효성 검사 규칙이 있을 수 있다
 * - 각 역할을 명확히 분리해 유지보수성을 높인다
 */
@Getter
@NoArgsConstructor
public class UpdateTodoRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
    private String title;

    @Size(max = 500, message = "설명은 500자 이하여야 합니다.")
    private String description;
}
