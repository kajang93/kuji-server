// 사업자 등록증을 공개 경로 밖에 저장하고 인증된 다운로드에 제공합니다.
package com.kuji.backend.domain.business.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.UUID;

@Service
public class BusinessLicenseStorage {
    private static final String PREFIX = "private-license:";
    private static final long MAX_SIZE = 5 * 1024 * 1024;
    private final Path directory;

    public BusinessLicenseStorage(@Value("${file.business-license-dir:./private/business-licenses}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("사업자 등록증은 5MB 이하의 JPG, PNG, PDF 파일을 첨부해 주세요.");
        }
        try {
            byte[] bytes = file.getBytes();
            String extension = detectExtension(bytes);
            String expectedType = switch (extension) {
                case "jpg" -> "image/jpeg";
                case "png" -> "image/png";
                default -> "application/pdf";
            };
            if (!expectedType.equals(file.getContentType())) {
                throw new IllegalArgumentException("사업자 등록증의 파일 형식이 올바르지 않습니다.");
            }
            Files.createDirectories(directory);
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
            Path path = Files.createFile(directory.resolve(UUID.randomUUID() + "." + extension));
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            try {
                Files.write(path, bytes);
            } catch (IOException e) {
                Files.deleteIfExists(path);
                throw e;
            }
            String key = PREFIX + path.getFileName();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) delete(key);
                    }
                });
            }
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("사업자 등록증을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private String detectExtension(byte[] bytes) {
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) return "png";
        if (bytes.length >= 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255) return "jpg";
        if (bytes.length >= 5 && new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) return "pdf";
        throw new IllegalArgumentException("사업자 등록증은 JPG, PNG, PDF 파일만 첨부할 수 있습니다.");
    }

    public boolean isStored(String key) {
        Path path = resolve(key);
        return path != null && Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS);
    }

    public Resource open(String key) {
        if (!isStored(key)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "등록증이 없습니다. 파일을 다시 제출해 주세요.");
        return new FileSystemResource(resolve(key));
    }

    public MediaType contentType(String key) {
        return key.endsWith(".pdf") ? MediaType.APPLICATION_PDF : key.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
    }

    public void deleteAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { delete(key); }
            });
        }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("private-license:[0-9a-f-]{36}\\.(jpg|png|pdf)")) return null;
        Path path = directory.resolve(key.substring(PREFIX.length())).normalize();
        return path.getParent().equals(directory) ? path : null;
    }

    private void delete(String key) {
        Path path = resolve(key);
        if (path != null) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { /* 저장 기록은 유지되므로 운영자가 재정리할 수 있습니다. */ }
        }
    }
}
