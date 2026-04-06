package com.study.todo_spring.global.exception;

import lombok.Getter;

/**
 * CustomException - 커스텀 비즈니스 예외 클래스
 *
 * [Java 예외 계층 구조]
 * Throwable
 *  ├── Error           (시스템 레벨 오류, 잡으면 안 됨)
 *  └── Exception
 *       ├── Checked Exception   (컴파일러가 처리 강제 - IOException, SQLException 등)
 *       └── RuntimeException    (Unchecked - NullPointerException, IllegalArgumentException 등)
 *
 * RuntimeException을 상속하면 throws 선언 없이 어디서든 던질 수 있다.
 * Spring의 @Transactional은 기본적으로 RuntimeException 발생 시 롤백한다.
 *
 * [NestJS 비교]
 * NestJS:  throw new HttpException('메시지', HttpStatus.NOT_FOUND)
 *          또는 throw new NotFoundException('메시지')
 * Spring:  throw new CustomException(ErrorCode.TODO_NOT_FOUND)
 *
 * Spring 방식의 장점: ErrorCode Enum으로 에러 코드가 중앙 관리된다.
 */
@Getter
public class CustomException extends RuntimeException {

    /** 어떤 종류의 에러인지 (HTTP 상태코드와 메시지를 포함) */
    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        // RuntimeException의 message로 에러 메시지 전달 (로그에 출력됨)
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
