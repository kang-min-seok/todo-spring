package com.study.todo_spring.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * GlobalExceptionHandler - 전역 예외 처리기
 *
 * [@RestControllerAdvice 란?]
 * 애플리케이션 전체에서 발생하는 예외를 한 곳에서 잡아서 처리한다.
 * @ControllerAdvice + @ResponseBody 의 조합이다.
 *   - @ControllerAdvice : 모든 @Controller에 적용되는 AOP(관점 지향 프로그래밍) 컴포넌트
 *   - @ResponseBody     : 반환 값을 JSON으로 직렬화
 *
 * [NestJS 비교]
 * NestJS의 ExceptionFilter + @Catch() 데코레이터와 동일한 역할이다.
 *
 * NestJS:
 *   @Catch(HttpException)
 *   export class HttpExceptionFilter implements ExceptionFilter {
 *     catch(exception: HttpException, host: ArgumentsHost) { ... }
 *   }
 *
 * Spring:
 *   @RestControllerAdvice
 *   public class GlobalExceptionHandler {
 *     @ExceptionHandler(CustomException.class)
 *     public ResponseEntity<ErrorResponse> handle(CustomException e) { ... }
 *   }
 *
 * NestJS는 main.ts에서 app.useGlobalFilters()로 등록해야 하지만,
 * Spring은 @RestControllerAdvice만 붙이면 @ComponentScan이 자동으로 Bean으로 등록한다.
 *
 * [AOP(Aspect Oriented Programming) 개념]
 * 여러 Controller에 공통으로 적용해야 하는 로직(예외 처리, 로깅)을
 * 각 Controller마다 직접 작성하지 않고 한 곳에서 횡단으로 처리하는 방식이다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * CustomException 처리
     * throw new CustomException(ErrorCode.TODO_NOT_FOUND) 이 발생하면 여기서 잡힌다.
     *
     * @ExceptionHandler : 어떤 예외 클래스를 처리할지 지정
     * ResponseEntity<T> : HTTP 상태 코드와 응답 바디를 함께 반환하는 래퍼 클래스
     *                     NestJS에서 res.status(404).json({...}) 와 동일
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())           // HTTP 상태 코드 설정
                .body(ErrorResponse.builder()
                        .status(errorCode.getStatus().value())
                        .code(errorCode.name())           // Enum 이름 ("TODO_NOT_FOUND")
                        .message(errorCode.getMessage())
                        .build());
    }

    /**
     * @Valid 유효성 검사 실패 처리
     * @RequestBody에 @Valid가 붙어있고 검사에 실패하면 MethodArgumentNotValidException 이 발생한다.
     *
     * [NestJS 비교]
     * NestJS의 ValidationPipe가 class-validator 오류를 잡아 400 응답을 보내는 것과 동일하다.
     * Spring은 이 예외를 직접 핸들러에서 처리해야 한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        // 첫 번째 필드 에러 메시지를 꺼낸다 ("title: 제목은 필수입니다." 형식)
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("유효성 검사에 실패했습니다.");

        return ResponseEntity
                .badRequest()  // 400 Bad Request
                .body(ErrorResponse.builder()
                        .status(400)
                        .code("VALIDATION_ERROR")
                        .message(message)
                        .build());
    }

    /**
     * 처리되지 않은 모든 예외 처리 (최후의 방어선)
     * 예상치 못한 서버 에러가 스택 트레이스째 클라이언트에 노출되는 것을 막는다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        return ResponseEntity
                .internalServerError()  // 500 Internal Server Error
                .body(ErrorResponse.builder()
                        .status(500)
                        .code(ErrorCode.INTERNAL_SERVER_ERROR.name())
                        .message(ErrorCode.INTERNAL_SERVER_ERROR.getMessage())
                        .build());
    }
}
