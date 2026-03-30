package com.kuji.backend.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // 💡 "이 식당(프로젝트)에서 터지는 모든 에러는 내가 다 낚아챈다!"
public class GlobalExceptionHandler {

    /**
     * 우리가 MemberService에서 던진 IllegalArgumentException을 전담 마크!
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {

        // 프론트엔드가 알기 쉽게 400 (Bad Request) 상태 코드와, 우리가 던진 메시지만 예쁘게 포장
        ErrorResponse response = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}