package com.study.todo_spring.todo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CreateTodoRequest - Todo 생성 요청 DTO
 *
 * [DTO(Data Transfer Object)란?]
 * 레이어 간 데이터를 전달하는 전용 객체다.
 * 도메인 객체(Todo)를 직접 외부에 노출하지 않고, DTO를 통해 필요한 데이터만 주고받는다.
 * NestJS에서 class-validator 데코레이터를 붙인 DTO 클래스와 완전히 동일한 개념이다.
 *
 * [NestJS 비교]
 * NestJS:
 *   export class CreateTodoDto {
 *     @IsNotEmpty() @MaxLength(100)
 *     title: string;
 *   }
 * Spring:
 *   public class CreateTodoRequest {
 *     @NotBlank @Size(max = 100)
 *     private String title;
 *   }
 * 어노테이션만 다를 뿐 구조가 거의 동일하다.
 *
 * [Lombok 어노테이션]
 * @Getter        : getter 자동 생성
 * @NoArgsConstructor : 기본 생성자 자동 생성 (Jackson이 JSON → 객체 변환 시 필요)
 *
 * [Jakarta Bean Validation 어노테이션]
 * Spring Boot 3.x부터 javax.* → jakarta.* 패키지로 변경되었다.
 */
@Getter
@NoArgsConstructor
public class CreateTodoRequest {

    /**
     * @NotBlank : null, 빈 문자열(""), 공백(" ") 모두 거부
     * NestJS의 @IsNotEmpty() + @IsString() 조합과 동일
     */
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
    private String title;

    /**
     * @Size : 문자열 길이 제한
     * description은 필수가 아니므로 @NotBlank 없이 길이만 제한
     */
    @Size(max = 500, message = "설명은 500자 이하여야 합니다.")
    private String description;
}
