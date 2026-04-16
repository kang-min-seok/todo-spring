package com.study.todo_spring.todo.repository;

import com.study.todo_spring.todo.domain.Todo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * TodoRepository - Todo 데이터 접근 레이어
 *
 * [JpaRepository<Entity타입, PK타입>]
 * 이 인터페이스를 상속하는 것만으로 기본 CRUD 메서드가 자동으로 제공된다.
 * 별도의 구현 클래스를 작성하지 않아도 Spring이 런타임에 구현체를 생성해서 Bean으로 등록한다.
 *
 * [NestJS 비교]
 * TypeORM에서 @InjectRepository(Todo)로 주입받는 Repository<Todo>와 동일한 역할.
 * 차이점: TypeORM은 구현이 이미 있는 Repository를 주입받지만,
 *        Spring Data JPA는 인터페이스 선언만으로 구현체를 자동 생성한다.
 *
 * 기본 제공 메서드 (별도 구현 없이 사용 가능):
 *   findAll()                   → SELECT * FROM todos
 *   findAll(Pageable)           → SELECT * FROM todos LIMIT ? OFFSET ?
 *   findById(Long id)           → SELECT * FROM todos WHERE id = ?  (Optional 반환)
 *   save(Todo todo)             → INSERT (id 없을 때) / UPDATE (id 있을 때)
 *   delete(Todo todo)           → DELETE FROM todos WHERE id = ?
 *   count()                     → SELECT COUNT(*) FROM todos
 *   existsById(Long id)         → SELECT EXISTS(SELECT 1 FROM todos WHERE id = ?)
 */
public interface TodoRepository extends JpaRepository<Todo, Long> {

    /**
     * 제목에 키워드가 포함된 Todo를 페이지네이션으로 조회
     *
     * [메서드 네이밍 쿼리]
     * 메서드 이름을 규칙에 맞게 작성하면 Spring Data JPA가 자동으로 JPQL을 생성한다.
     * 별도의 @Query 없이도 동작한다.
     *
     * findBy          → WHERE 절 시작
     * Title           → title 필드
     * Containing      → LIKE '%keyword%'
     * IgnoreCase      → 대소문자 무시
     *
     * 생성되는 쿼리:
     * SELECT * FROM todos WHERE UPPER(title) LIKE UPPER('%keyword%')
     *
     * NestJS TypeORM의 findBy({ title: Like('%keyword%') })와 동일한 결과
     */
    Page<Todo> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);
}
