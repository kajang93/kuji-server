package com.kuji.backend.domain.member.dto;

public record LoginRequest(
        String email,
        String password) {
}