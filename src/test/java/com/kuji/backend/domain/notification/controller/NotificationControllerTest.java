package com.kuji.backend.domain.notification.controller;

import com.kuji.backend.domain.notification.dto.NotificationResponse;
import com.kuji.backend.domain.notification.dto.TokenRequest;
import com.kuji.backend.domain.notification.entity.NotificationType;
import com.kuji.backend.domain.notification.service.NotificationService;
import com.kuji.backend.global.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtUtil jwtUtil;

    private String validToken;

    @BeforeEach
    void setUp() {
        validToken = "valid.jwt.token";
        when(jwtUtil.validateToken(validToken)).thenReturn(true);
        when(jwtUtil.getMemberId(validToken)).thenReturn(1L);
        when(jwtUtil.getRole(validToken)).thenReturn("USER");
    }

    @Test
    @DisplayName("기기 토큰 등록 API 테스트")
    void registerToken() throws Exception {
        // given
        String requestJson = "{\"token\":\"fcm_token_value\",\"platform\":\"WEB\",\"deviceId\":\"device_id_value\"}";

        // when & then
        mockMvc.perform(post("/api/notifications/token")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(notificationService, times(1)).registerToken(eq(1L), any(TokenRequest.class));
    }

    @Test
    @DisplayName("기기 토큰 삭제 API 테스트")
    void deleteToken() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/notifications/token/device_id_value")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());

        verify(notificationService, times(1)).deleteToken(1L, "device_id_value");
    }

    @Test
    @DisplayName("내 알림 목록 조회 API 테스트")
    void getNotifications() throws Exception {
        // given
        NotificationResponse response = NotificationResponse.builder()
                .id(100L)
                .title("테스트 알림")
                .body("테스트 알림 내용")
                .type(NotificationType.COMMENT)
                .targetId("1")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        PageImpl<NotificationResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1);

        when(notificationService.getMyNotifications(eq(1L), any(Pageable.class))).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(jsonPath("$.content[0].title").value("테스트 알림"))
                .andExpect(jsonPath("$.content[0].body").value("테스트 알림 내용"));
    }

    @Test
    @DisplayName("특정 알림 읽음 처리 API 테스트")
    void readNotification() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/notifications/100/read")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());

        verify(notificationService, times(1)).readNotification(1L, 100L);
    }

    @Test
    @DisplayName("모든 알림 읽음 처리 API 테스트")
    void readAllNotifications() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());

        verify(notificationService, times(1)).readAllNotifications(1L);
    }
}
