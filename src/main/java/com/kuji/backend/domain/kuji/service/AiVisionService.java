package com.kuji.backend.domain.kuji.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuji.backend.global.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiVisionService {

    private final S3Service s3Service;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${huggingface.api-key}")
    private String hfApiKey;

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.model}")
    private String geminiModel;

    /**
     * 배경을 제거하고, AI 분석(칭호/설명)을 수행한 뒤 결과를 반환합니다.
     */
    public Map<String, Object> processPrizeImage(MultipartFile file) throws Exception {
        // 1. Hugging Face로 배경 제거
        byte[] noBgImageBytes = removeBackground(file);
        
        // 2. 배경 지워진 이미지를 S3에 업로드
        String imageUrl = s3Service.uploadFile("prizes", file.getOriginalFilename() + "_nobg.png", noBgImageBytes, "image/png");

        // 3. 배경 지워진 이미지를 Gemini로 분석하여 캡션(칭호/스탯) 생성
        Map<String, String> aiStats = generateCaptionAndStats(noBgImageBytes);

        Map<String, Object> result = new HashMap<>();
        result.put("imageUrl", imageUrl);
        result.put("name", aiStats.getOrDefault("name", "알 수 없는 아이템"));
        result.put("description", aiStats.getOrDefault("description", "설명이 없습니다."));
        return result;
    }

    /**
     * Hugging Face RMBG-1.4 모델을 호출하여 배경 제거
     */
    private byte[] removeBackground(MultipartFile file) throws Exception {
        if ("mock".equals(hfApiKey)) {
            log.warn("Hugging Face API Key가 설정되지 않았습니다. 원본 이미지를 그대로 사용합니다.");
            return file.getBytes();
        }

        String url = "https://api-inference.huggingface.co/models/briaai/RMBG-1.4";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(hfApiKey);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        HttpEntity<byte[]> request = new HttpEntity<>(file.getBytes(), headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.postForEntity(url, request, byte[].class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("배경 제거 중 오류 발생 (무료 할당량 초과 등): {}", e.getMessage());
            // 실패 시 원본 반환
        }
        return file.getBytes();
    }

    /**
     * Gemini 1.5 Flash 모델에 이미지를 전송하여 JSON 형태로 이름/설명 추출
     */
    private Map<String, String> generateCaptionAndStats(byte[] imageBytes) {
        if ("mock".equals(geminiApiKey)) {
            return Map.of("name", "테스트 상품", "description", "제미나이 키가 설정되지 않았습니다.");
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        // 프롬프트: JSON 형태로 답변을 유도
        String prompt = "당신은 애니메이션/피규어 상품 전문가입니다. 이 사진에 있는 상품(피규어/굿즈 등)의 멋진 '이름'과 그에 어울리는 '설명(전투력이나 희귀도 포함)'을 한국어로 작성해주세요. 반드시 다음 JSON 형식으로만 응답해야 합니다: {\"name\": \"멋진 이름\", \"description\": \"멋진 설명\"}";

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> imagePart = Map.of(
                "inlineData", Map.of(
                        "mimeType", "image/png",
                        "data", base64Image
                )
        );

        Map<String, Object> contentMap = Map.of("parts", List.of(textPart, imagePart));
        Map<String, Object> body = Map.of("contents", List.of(contentMap));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null && responseBody.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    if (!parts.isEmpty()) {
                        String rawText = (String) parts.get(0).get("text");
                        // JSON 블록(```json ... ```) 제거
                        rawText = rawText.replaceAll("```json", "").replaceAll("```", "").trim();
                        return objectMapper.readValue(rawText, Map.class);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Gemini AI 분석 중 오류 발생: {}", e.getMessage());
        }

        return Map.of("name", "자동 분석 실패", "description", "분석을 완료하지 못했습니다.");
    }
}
