package com.kuji.backend.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-json:}")
    private String serviceAccountJson;

    @Value("${firebase.service-account-base64:}")
    private String serviceAccountBase64;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount = resolveServiceAccount();
                if (serviceAccount == null) {
                    log.warn("[FirebaseConfig] Firebase 서비스 계정이 없어 FCM 알림 기능이 비활성화됩니다. FIREBASE_SERVICE_ACCOUNT_JSON 또는 FIREBASE_SERVICE_ACCOUNT_BASE64 환경변수를 설정하세요.");
                    return;
                }

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("[FirebaseConfig] Firebase Admin SDK 초기화 성공!");
            }
        } catch (Exception e) {
            log.error("[FirebaseConfig] Firebase Admin SDK 초기화 실패: {}", e.getMessage());
        }
    }

    private InputStream resolveServiceAccount() throws Exception {
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            log.info("[FirebaseConfig] 환경변수 JSON으로 Firebase 서비스 계정을 로드합니다.");
            return new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
        }

        if (serviceAccountBase64 != null && !serviceAccountBase64.isBlank()) {
            log.info("[FirebaseConfig] 환경변수 Base64로 Firebase 서비스 계정을 로드합니다.");
            byte[] decoded = Base64.getDecoder().decode(serviceAccountBase64);
            return new ByteArrayInputStream(decoded);
        }

        ClassPathResource resource = new ClassPathResource("firebase-service-account.json");
        if (!resource.exists()) {
            return null;
        }

        log.info("[FirebaseConfig] classpath firebase-service-account.json으로 Firebase 서비스 계정을 로드합니다.");
        return resource.getInputStream();
    }
}
