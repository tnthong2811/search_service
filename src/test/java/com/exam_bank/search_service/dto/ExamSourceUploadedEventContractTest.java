package com.exam_bank.search_service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExamSourceUploadedEvent contract tests")
class ExamSourceUploadedEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserialize_whenProducerJsonIncludesAllFields_thenMapAllSevenFields() throws Exception {
        String json = """
                {
                  "examId": 1001,
                  "fileObjectName": "uploads/1001/page-1.pdf",
                  "originalFileName": "de-thi-hoc-ky.pdf",
                  "uploadedByUserId": "42",
                  "objectKeys": ["uploads/1001/page-1.pdf", "uploads/1001/page-2.pdf"],
                  "pageCount": 2,
                  "uploadRequestId": 555
                }
                """;

        ExamSourceUploadedEvent event = objectMapper.readValue(json, ExamSourceUploadedEvent.class);

        assertThat(event.getExamId()).isEqualTo(1001L);
        assertThat(event.getFileObjectName()).isEqualTo("uploads/1001/page-1.pdf");
        assertThat(event.getOriginalFileName()).isEqualTo("de-thi-hoc-ky.pdf");
        assertThat(event.getUploadedByUserId()).isEqualTo("42");
        assertThat(event.getObjectKeys()).containsExactly("uploads/1001/page-1.pdf", "uploads/1001/page-2.pdf");
        assertThat(event.getPageCount()).isEqualTo(2);
        assertThat(event.getUploadRequestId()).isEqualTo(555L);
    }

    @Test
    void roundTrip_whenSerializeThenDeserialize_thenPreserveAllFields() throws Exception {
        ExamSourceUploadedEvent original = ExamSourceUploadedEvent.builder()
                .examId(77L)
                .fileObjectName("uploads/77/page-1.pdf")
                .originalFileName("midterm.pdf")
                .uploadedByUserId("123")
                .objectKeys(List.of("uploads/77/page-1.pdf", "uploads/77/page-2.pdf", "uploads/77/page-3.pdf"))
                .pageCount(3)
                .uploadRequestId(9999L)
                .build();

        String serialized = objectMapper.writeValueAsString(original);
        ExamSourceUploadedEvent restored = objectMapper.readValue(serialized, ExamSourceUploadedEvent.class);

        assertThat(restored)
                .usingRecursiveComparison()
                .isEqualTo(original);
    }
}
