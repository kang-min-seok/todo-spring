package com.study.todo_spring.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JpaConfig - JPA 전역 설정
 *
 * [@EnableJpaAuditing]
 * @CreatedDate, @LastModifiedDate 어노테이션이 실제로 동작하도록 Auditing 기능을 활성화한다.
 * 이 어노테이션이 없으면 @CreatedDate를 붙여도 createdAt 컬럼에 null이 들어간다.
 *
 * TodoSpringApplication.java에 직접 붙이는 방법도 있지만,
 * 설정 클래스를 분리하면 테스트 환경에서 JPA Auditing을 선택적으로 적용할 수 있어 더 낫다.
 *
 * [@Configuration]
 * 이 클래스가 Spring 설정 클래스임을 선언한다.
 * @EnableJpaAuditing 같은 활성화 어노테이션은 반드시 @Configuration 클래스에 붙어야 한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
