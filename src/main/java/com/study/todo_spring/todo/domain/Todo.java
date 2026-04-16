package com.study.todo_spring.todo.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Todo - 할 일 JPA Entity
 *
 * Phase 1에서는 순수 Java 객체(POJO)였지만,
 * Phase 2에서 @Entity를 추가해 DB 테이블과 매핑되는 영속 객체가 됐다.
 * 코드 변경은 어노테이션 추가뿐이고, update()/complete() 비즈니스 메서드는 그대로 유지된다.
 *
 * [NestJS 비교]
 * TypeORM의 @Entity() 클래스와 동일한 역할이다.
 *
 * [@EntityListeners(AuditingEntityListener.class)]
 * @CreatedDate, @LastModifiedDate가 동작하려면 반드시 필요하다.
 * JpaConfig의 @EnableJpaAuditing과 함께 동작한다.
 * NestJS TypeORM의 @CreateDateColumn(), @UpdateDateColumn()은 별도 설정 없이 동작하지만
 * Spring JPA는 Auditing을 명시적으로 활성화해야 한다.
 *
 * [@Builder + JPA 주의사항]
 * JPA는 Entity를 조회할 때 기본 생성자로 인스턴스를 만든 뒤 필드를 채운다.
 * @Builder만 있으면 기본 생성자가 없어서 JPA가 객체를 만들지 못한다.
 * → @NoArgsConstructor : JPA용 기본 생성자 (protected로 외부 직접 생성 방지)
 * → @AllArgsConstructor : @Builder가 내부적으로 사용하는 전체 생성자
 */
@Entity
@Table(name = "todos")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Todo {

    /**
     * @Id : PRIMARY KEY
     * @GeneratedValue(strategy = GenerationType.IDENTITY) : AUTO_INCREMENT
     * NestJS TypeORM의 @PrimaryGeneratedColumn()과 동일
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * @Column : 컬럼 속성 지정
     * nullable = false → NOT NULL 제약조건
     * length = 100     → VARCHAR(100). 기본값은 255
     */
    @Column(nullable = false, length = 100)
    private String title;

    /**
     * columnDefinition = "TEXT" : 컬럼 타입을 TEXT로 지정
     * description은 null 허용 (nullable = true가 기본값)
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean completed;

    /**
     * @CreatedDate : INSERT 시 현재 시간 자동 저장
     * updatable = false : UPDATE 시 이 컬럼은 변경하지 않음
     * NestJS TypeORM의 @CreateDateColumn()과 동일
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * @LastModifiedDate : UPDATE 시 현재 시간 자동 갱신
     * NestJS TypeORM의 @UpdateDateColumn()과 동일
     */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ── 비즈니스 메서드 ─────────────────────────────────────────────────────────

    /**
     * 제목과 설명 수정.
     * @Transactional 안에서 이 메서드를 호출하면 save() 없이도
     * 트랜잭션 종료 시 Hibernate가 변경을 감지해 UPDATE 쿼리를 자동 실행한다. (Dirty Checking)
     */
    public void update(String title, String description) {
        this.title = title;
        this.description = description;
    }

    /**
     * 완료 상태로 변경.
     * updatedAt은 @LastModifiedDate가 자동으로 갱신하므로 직접 설정하지 않아도 된다.
     */
    public void complete() {
        this.completed = true;
    }
}
