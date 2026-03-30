package com.kuji.backend.global.exception;

// 💡 프론트엔드에서 받기 가장 편한 형태로 규격을 딱 정해둡니다.
public record ErrorResponse(
        int status,
        String message) {
}