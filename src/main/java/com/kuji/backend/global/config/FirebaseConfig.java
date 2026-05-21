package com.kuji.backend.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                ClassPathResource resource = new ClassPathResource("firebase-service-account.json");

                // 만약 파일이 없다면 에러가 나지 않도록 로깅만 하고 넘깁니다. (유저가 아직 다운받지 않았을 수 있으므로)
                if (!resource.exists()) {
                    log.warn("[FirebaseConfig] firebase-service-account.json 파일을 찾을 수 없습니다. FCM 알림 기능이 비활성화됩니다.");
                    return;
                }

                InputStream serviceAccount = resource.getInputStream();

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
}
