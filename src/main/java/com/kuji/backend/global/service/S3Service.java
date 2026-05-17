package com.kuji.backend.global.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    /**
     * S3에 파일 업로드
     */
    public String uploadFile(String category, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String fileName = category + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // 업로드된 파일의 공개 URL 반환 (CloudFront 사용 시 해당 URL로 변경 가능)
            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, fileName);

        } catch (IOException e) {
            throw new RuntimeException("S3 파일 업로드 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * S3에서 파일 삭제
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }

        // URL에서 키 추출 (예: https://bucket.s3.region.amazonaws.com/posts/uuid_file.jpg -> posts/uuid_file.jpg)
        // 버킷명과 리전에 따라 형식이 다를 수 있으므로 마지막 슬래시(/) 이후가 아닌 버킷 주소 이후를 추출해야 함
        try {
            String baseUrl = String.format("https://%s.s3.%s.amazonaws.com/", bucket, region);
            String key = fileUrl.replace(baseUrl, "");

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
        } catch (Exception e) {
            // 삭제 실패 시 로그만 남기고 예외를 던지지 않음 (DB 삭제가 더 중요하므로)
            System.err.println("S3 파일 삭제 실패: " + fileUrl + ", 에러: " + e.getMessage());
        }
    }
}
