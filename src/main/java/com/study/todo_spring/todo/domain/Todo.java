package com.study.todo_spring.todo.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Todo - 할 일 도메인 객체 (인메모리 버전)
 *
 * [NestJS 비교]
 * Phase 2(JPA)에서는 @Entity 어노테이션을 붙여 DB 테이블과 매핑되는 클래스가 되지만,
 * 지금은 순수한 Java 객체(POJO)로만 사용한다.
 * NestJS + TypeORM의 @Entity() 클래스와 같은 역할이 될 것이다.
 *
 * [Lombok 어노테이션]
 * @Getter : 모든 필드의 getter 메서드를 컴파일 시 자동 생성 (getId(), getTitle() 등)
 * @Builder : Builder 패턴 코드를 자동 생성 → Todo.builder().id(1L).title("제목").build()
 */
@Getter
@Builder
public class Todo {

    private Long id;
    private String title;
    private String description;
    private boolean completed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 제목과 설명을 수정한다.
     * 도메인 로직을 엔티티 내부에 캡슐화하는 것이 객체지향적 설계다.
     * (Service에서 필드를 직접 건드리는 방식보다 이 방식을 권장)
     */
    public void update(String title, String description) {
        this.title = title;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 할 일을 완료 상태로 변경한다.
     */
    public void complete() {
        this.completed = true;
        this.updatedAt = LocalDateTime.now();
    }
}
