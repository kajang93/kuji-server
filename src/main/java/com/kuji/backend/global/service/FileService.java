package com.kuji.backend.global.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    public String saveFile(String category, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            // 1. 카테고리별/날짜별 디렉토리 생성 (예: uploads/boards/2026-04/)
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            Path rootPath = Paths.get(uploadDir, category, datePath);

            if (!Files.exists(rootPath)) {
                Files.createDirectories(rootPath);
            }

            // 2. 파일명 생성 (UUID 사용)
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String savedFilename = UUID.randomUUID().toString() + extension;

            // 3. 파일 저장
            Path targetPath = rootPath.resolve(savedFilename);
            file.transferTo(targetPath);

            // 4. 접근 URL 경로 반환 (예: /uploads/boards/2026-04/uuid.jpg)
            return String.format("/uploads/%s/%s/%s", category, datePath, savedFilename);

        } catch (IOException e) {
            throw new RuntimeException("파일 저장 중 오류가 발생했습니다.", e);
        }
    }
}
