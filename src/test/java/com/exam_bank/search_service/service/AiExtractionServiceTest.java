package com.exam_bank.search_service.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AiExtractionService response parsing")
class AiExtractionServiceTest {

  private final AiExtractionService service = new AiExtractionService(null);

  @Test
  void extractTextFromResponseBody_whenValidGeminiPayload_thenReturnFirstTextPart() {
    String body = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  { "text": "[{\\\"content\\\":\\\"Q1\\\"}]" }
                ]
              }
            }
          ]
        }
        """;

    String text = service.extractTextFromResponseBody(body);

    assertThat(text).isEqualTo("[{\"content\":\"Q1\"}]");
  }

  @Test
  void extractTextFromResponseBody_whenBodyBlank_thenThrowMeaningfulError() {
    assertThatThrownBy(() -> service.extractTextFromResponseBody("   "))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Gemini response body is empty");
  }

  @Test
  void extractTextFromResponseBody_whenCandidatesMissing_thenThrowMeaningfulError() {
    String body = """
        {
          "candidates": []
        }
        """;

    assertThatThrownBy(() -> service.extractTextFromResponseBody(body))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Gemini response has no candidates");
  }
}
