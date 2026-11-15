package com.kuji.backend.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // 💡 "이 식당(프로젝트)에서 터지는 모든 에러는 내가 다 낚아챈다!"
public class GlobalExceptionHandler {

    // 업로드 용량 초과 (파일 5MB, 요청 20MB) 는 원인을 알 수 있는 메시지로 돌려준다
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE.value(), "이미지 용량이 너무 큽니다. 한 장 5MB 이하로 올려 주세요."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(HttpStatus.FORBIDDEN.value(), e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(new ErrorResponse(e.getStatusCode().value(), e.getReason()));
    }

    /**
     * 우리가 MemberService에서 던진 IllegalArgumentException을 전담 마크!
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {

        // 프론트엔드가 알기 쉽게 400 (Bad Request) 상태 코드와, 우리가 던진 메시지만 예쁘게 포장
        ErrorResponse response = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 판매 중지·재고 부족·사업자 미승인처럼 지금 상태 때문에 처리할 수 없는 요청은 409와 함께 이유를 돌려준다.
     * 라이브러리 내부 오류처럼 한글 안내가 없는 메시지는 내부 정보가 새지 않도록 일반 문구로 바꾼다.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException e) {
        String message = e.getMessage();
        if (message == null || !message.matches("(?s).*[가-힣].*")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), message));
    }
}
