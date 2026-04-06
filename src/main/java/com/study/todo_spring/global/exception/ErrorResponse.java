package com.study.todo_spring.global.exception;

import lombok.Builder;
import lombok.Getter;

/**
 * ErrorResponse - 에러 응답 DTO
 *
 * 클라이언트에게 반환하는 에러 응답의 형식을 정의한다.
 * 모든 에러 응답이 동일한 구조를 가지도록 통일한다.
 *
 * 응답 예시:
 * {
 *   "status": 404,
 *   "code": "TODO_NOT_FOUND",
 *   "message": "해당 Todo를 찾을 수 없습니다."
 * }
 *
 * [NestJS 비교]
 * NestJS의 기본 에러 응답:
 * { "statusCode": 404, "message": "...", "error": "Not Found" }
 * Spring은 기본 에러 형식이 없으므로 직접 정의해야 한다.
 * (이게 번거롭지만 응답 형식을 자유롭게 커스텀할 수 있다는 장점이 있다)
 */
@Getter
@Builder
public class ErrorResponse {

    private int status;     // HTTP 상태 코드 숫자 (404, 400 등)
    private String code;    // 에러 코드 문자열 ("TODO_NOT_FOUND" 등) - 프론트에서 분기 처리용
    private String message; // 사람이 읽을 수 있는 에러 메시지
}
