package com.study.todo_spring.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CorsConfig - CORS(Cross-Origin Resource Sharing) 전역 설정
 *
 * 프론트엔드(예: localhost:5173)와 백엔드(localhost:8080)가 다른 포트에서 실행될 때
 * 브라우저의 Same-Origin Policy에 의해 요청이 차단된다.
 * 이 설정으로 특정 출처의 요청을 허용한다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")           // /api/ 하위 모든 경로에 적용
                .allowedOrigins(
                        "http://localhost:5173",          // Vite 기본 포트
                        "http://localhost:3000",          // 기타 로컬 개발 포트
                        "https://todo-study.pages.dev"   // Cloudflare Pages 프로덕션
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
