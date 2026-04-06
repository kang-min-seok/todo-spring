package com.study.todo_spring.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * ErrorCode - 애플리케이션 에러 코드 열거형(Enum)
 *
 * [설계 의도]
 * 에러 코드, HTTP 상태 코드, 메시지를 한 곳에서 관리한다.
 * 새로운 에러를 추가할 때 이 파일만 수정하면 된다.
 *
 * [NestJS 비교]
 * NestJS에서는 HttpException 또는 NotFoundException 같은 내장 예외를 직접 던지거나,
 * 커스텀 예외 클래스마다 status와 message를 선언한다.
 * Spring에서는 이 Enum 패턴이 관리가 용이해 널리 쓰인다.
 *
 * [Lombok 어노테이션]
 * @RequiredArgsConstructor : final 필드를 인자로 받는 생성자 자동 생성
 *                            → ErrorCode(HttpStatus status, String message) 생성자가 만들어진다
 * @Getter                  : getStatus(), getMessage() 자동 생성
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── Todo 관련 에러 ───────────────────────────────────────────────────────
    TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 Todo를 찾을 수 없습니다."),

    // ── 공통 에러 ────────────────────────────────────────────────────────────
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;  // HTTP 응답 상태 코드 (404, 400, 500 등)
    private final String message;     // 사용자에게 보여줄 에러 메시지
}
