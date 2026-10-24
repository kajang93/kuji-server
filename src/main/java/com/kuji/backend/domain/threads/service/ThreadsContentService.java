package com.kuji.backend.domain.threads.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.kuji.backend.global.infra.ExternalApiHttpClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
public class ThreadsContentService {

    @Value("${gemini.api-key:mock}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    private final RestTemplate restTemplate = ExternalApiHttpClient.createRestTemplate();
    private final Random random = new Random();

    // AI 실패 시 또는 오프라인 시 사용할 공감형 스레드 템플릿 풀
    private static final List<String> FALLBACK_POSTS = List.of(
            "굿즈 살 때 최애만 딱 사는 편이야, 차애까지 슬쩍 데려오는 편이야? 👀\n나는 분명 최애만 보러 갔는데 계산할 땐 식구가 늘어있더라 😌",
            "쿠지 뽑을 때 '딱 한 번만 더!' 외치고 진짜 딱 한 번으로 끝난 사람 있어...? 🫣\n판에 붙은 라스트상 눈 마주치면 손이 멈추질 않아 🔥",
            "최애 피규어 개봉파 vs 미개봉 소장파! 여러분의 선택은? 📦✨\n박스 뜯을 때의 그 설렘이냐, 박스째 전시하는 완벽한 보존이냐...",
            "오늘도 덕질 공간 정리하다가 새삼 감탄함... 언제 이렇게 증식했지? 🤣\n근데 신기하게 아직도 데려올 자리는 남아있단 말이지 🏠",
            "제일복권 할 때 A상 먼저 나오면 좋지만, 은근 아크릴 스탠드나 키링 맛집인 거 다들 공감하지? 🔑✨\n소소한 실사용 굿즈가 제일 오래 감!",
            "원하는 상 안 나왔을 때: '아... 돈 아깝다' (X) -> '다음 뽑기를 위한 액땜이었다' (O) 🤣\n이 긍정 회로가 없으면 오타쿠 못하지!",
            "쿠지 판에 라스트상까지 3개 남았을 때 지나칠 수 있는 사람 손 들어봐... 🙋‍♂️\n그건 참는 게 아니라 유죄야!",
            "퇴근길에 제일복권 한 판 하는 게 소소한 힐링인 사람? 🎲✨\n종이 뜯기 전 그 3초의 쫄깃함 때문에 현생 버틴다...",
            "최애 굿즈존 꾸밀 때 다이소 꿀템 필수인 거 알지? 🛠️\n아크릴 받침대랑 케이스 사러 갔다가 굿즈보다 다이소에서 더 많이 삼 😂",
            "가챠나 쿠지에서 원하던 거 원샷원킬했을 때 그 도파민은 그 어떤 것과도 비교 불가... 💥\n오늘 하루 운 다 쓴 기분 만끽 중!",
            "굿즈 박스 버릴 때 살짝 죄책감 드는 사람? 📦🥲\n결국 박스까지 차곡차곡 모아두는 엔딩...",
            "라스트상 실물 영접해보면 왜 다들 판털이하는지 바로 납득됨 🏆\n묵직함이 다르잖아 솔직히!",
            "굿즈 살 때 쓰는 돈은 '소비'가 아니라 '내 정신건강을 위한 투자'입니다 💊💖\n오늘도 내 행복을 샀다!",
            "스마트폰 앨범에 내 셀카보다 최애 사진이나 피규어 인증샷이 100배 많은 사람 조용히 고개 끄덕여 📸🙌");

    /**
     * 매일 오전 9시용 스레드 본문 생성
     * Gemini LLM으로 트렌디한 공감글을 생성하고, 실패 시 템플릿 사용
     */
    public String generateDailyPostContent() {
        if (!"mock".equalsIgnoreCase(geminiApiKey) && !geminiApiKey.isBlank()) {
            try {
                String aiContent = generateWithGemini();
                if (aiContent != null && !aiContent.isBlank()) {
                    return aiContent.trim();
                }
            } catch (Exception e) {
                log.warn("[Threads AI] Gemini 콘텐츠 생성 실패, 기본 템플릿으로 대체 ({})", e.getClass().getSimpleName());
            }
        }
        return getFallbackPost();
    }

    /**
     * 본문 게시 후 달릴 기본 첫 댓글 생성
     * 외부 링크 및 문의 링크를 포함
     */
    public String generateDefaultReplyContent() {
        return """
                ✨ 원하는 굿즈와 피규어를 언제 어디서나!
                오시쿠지 공식몰: https://kujishop.shop

                💬 입점 및 제휴/이용 문의:
                http://pf.kakao.com/_sdvGX
                """.trim();
    }

    private String generateWithGemini() {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key="
                + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String prompt = """
                너는 서브컬처/애니메이션 굿즈 및 쿠지(제일복권, 랜덤뽑기) 전문 브랜드 '오시쿠지(osikuji)'의 소셜미디어(스레드 Threads) 운영자야.

                스레드(Threads) 유저들이 크게 공감하고 댓글로 활발하게 대화하고 싶어지는 짧은 일상 공감 글 1개를 작성해줘.

                [작성 가이드]
                1. 주제: 애니메이션 덕질, 최애 굿즈 수집, 쿠지(제일복권) 뽑기할 때의 도파민과 실패담, 피규어 전시, 덕질 공간 등 일상적인 공감대
                2. 어투: 친근하고 편안한 반말 구어체 (~하는 편이야?, ~하더라 😌, ~있어? 등).
                3. 길이: 2~3문장 이내 (너무 길지 않게 스레드 특유의 호흡 유지).
                4. 마지막 문장은 질문이나 유쾌한 여운으로 남겨 댓글 반응을 유도할 것.
                5. 과하지 않게 적절한 이모지 1~2개 포함.
                6. 상업적인 광고나 링크는 절대 본문에 넣지 말 것 (댓글로 달 예정임).
                7. 출력 형식: 본문 텍스트만 깔끔하게 출력 (따옴표나 제목 없이 오직 본문만).
                """;

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> contentMap = Map.of("parts", List.of(textPart));
        Map<String, Object> body = Map.of("contents", List.of(contentMap));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && responseBody.containsKey("candidates")) {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
            if (!candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (!parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }
        }
        return null;
    }

    private String getFallbackPost() {
        int dayOfYear = LocalDate.now().getDayOfYear();
        int index = (dayOfYear + random.nextInt(3)) % FALLBACK_POSTS.size();
        return FALLBACK_POSTS.get(index);
    }
}
