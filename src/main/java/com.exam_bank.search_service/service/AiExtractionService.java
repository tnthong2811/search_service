package com.exam_bank.search_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiExtractionService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestClient restClient;

    // Tách prompt dùng chung ra để dùng cho cả Text và Ảnh
    private final String PROMPT_INSTRUCTIONS = """
            Bạn là một trợ lý AI giáo dục chuyên nghiệp.
            Nhiệm vụ của bạn là đọc nội dung đề thi được cung cấp và trích xuất tất cả các câu hỏi trắc nghiệm ra định dạng JSON.
            
            YÊU CẦU BẮT BUỘC:
            1. Chỉ trả về duy nhất một mảng JSON (JSON Array), KHÔNG bọc trong markdown (không dùng ```json), KHÔNG có bất kỳ văn bản giải thích nào khác.
            2. Cấu trúc mảng JSON phải đúng như sau:
            [
              {
                "content": "Nội dung câu hỏi",
                "explanation": "Giải thích chi tiết (nếu không có thì để trống)",
                "scoreWeight": 1,
                "options": [
                  { "content": "Nội dung đáp án A", "isCorrect": false },
                  { "content": "Nội dung đáp án B", "isCorrect": true }
                ]
              }
            ]
            3. Nếu có ký tự lỗi font, hãy cố gắng dịch và suy luận nó sang tiếng Việt chuẩn. Bắt buộc phải tìm mọi cách bóc tách ra ít nhất 1 câu hỏi, không được phép trả về mảng rỗng [].
            """;

    public AiExtractionService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(180000);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    // --- HÀM 1: XỬ LÝ TEXT (Giữ nguyên luồng cũ của bạn) ---
    public String extractQuestionsAsJson(String rawText) {
        log.info("Sending {} characters directly to Native Gemini API...", rawText.length());

        String prompt = PROMPT_INSTRUCTIONS + "\nNỘI DUNG ĐỀ THI:\n==================\n" + rawText + "\n==================";

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.1
                )
        );

        return executeWithRetry(requestBody);
    }

    // --- HÀM 2: XỬ LÝ ẢNH MỚI THÊM VÀO ---
    public String extractQuestionsFromImageAsJson(String base64Image, String mimeType) {
        log.info("Sending IMAGE to Native Gemini Vision API...");

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", PROMPT_INSTRUCTIONS),
                                Map.of("inlineData", Map.of(
                                        "mimeType", mimeType,
                                        "data", base64Image
                                ))
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.1
                )
        );

        return executeWithRetry(requestBody);
    }

    // --- HÀM DÙNG CHUNG: CHỨA ĐÚNG LOGIC GỌI API & RETRY ĐANG CHẠY TỐT CỦA BẠN ---
    private String executeWithRetry(Map<String, Object> requestBody) {
        String protocol = "http";
        String secure = "s://";
        String domain = "generativelanguage.googleapis.com";
        String path = "/v1beta/models/gemini-2.5-flash:generateContent?key=";
        String url = protocol + secure + domain + path + apiKey;

        int maxRetries = 5;
        int waitTimeMs = 5000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Map response = restClient.post()
                        .uri(url)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(Map.class);

                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                String textResult = (String) parts.get(0).get("text");

                if (textResult != null && textResult.startsWith("```json")) {
                    textResult = textResult.replaceAll("```json", "").replaceAll("```", "").trim();
                }

                log.info("Native AI Extraction completed successfully on attempt {}!", attempt);
                return textResult;

            } catch (RestClientResponseException e) {
                int statusCode = e.getStatusCode().value();
                if (statusCode == 503 || statusCode == 429) {
                    log.warn("Google API is busy (Status: {}). Attempt {}/{}. Waiting {} seconds before retrying...",
                            statusCode, attempt, maxRetries, waitTimeMs / 1000);
                    if (attempt == maxRetries) {
                        throw new RuntimeException("Gave up after " + maxRetries + " attempts.", e);
                    }
                    sleep(waitTimeMs);
                    waitTimeMs *= 2;
                } else {
                    log.error("Native AI Extraction failed with status {}: {}", statusCode, e.getResponseBodyAsString());
                    throw new RuntimeException("Failed to extract data from Native Gemini API", e);
                }
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (errorMsg.contains("timeout") || errorMsg.contains("reset")) {
                    log.warn("Connection timeout. Attempt {}/{}. Waiting {} seconds before retrying...",
                            attempt, maxRetries, waitTimeMs / 1000);
                    if (attempt == maxRetries) throw new RuntimeException("Connection consistently timing out.", e);
                    sleep(waitTimeMs);
                    waitTimeMs *= 2;
                } else {
                    log.error("Native AI Extraction failed with unexpected error: {}", e.getMessage());
                    throw new RuntimeException("Unexpected error during AI Extraction", e);
                }
            }
        }
        return null;
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}