package com.exam_bank.search_service;

import com.exam_bank.search_service.dto.ExamSourceUploadedEvent;
import com.exam_bank.search_service.listener.AiWorkerListener;
import com.exam_bank.search_service.service.AiExtractionService;
import com.exam_bank.search_service.service.EventPublisherService;
import com.exam_bank.search_service.service.MinioService;
import com.exam_bank.search_service.service.TikaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Smoke tests for search_service — lightweight end-to-end sanity checks
 * that the listener can process uploads and produce extraction results
 * without requiring external brokers or AI services.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("search_service smoke tests")
class SmokeTest {

    @Mock
    private MinioService minioService;

    @Mock
    private TikaService tikaService;

    @Mock
    private AiExtractionService aiExtractionService;

    @Mock
    private EventPublisherService eventPublisherService;

    @InjectMocks
    private AiWorkerListener listener;

    @Test
    @DisplayName("Smoke: single-page image upload produces success result")
    void smoke_singlePageImageUpload_producesSuccessResult() {
        ExamSourceUploadedEvent event = baseEventBuilder()
                .objectKeys(List.of("uploads/single.png"))
                .pageCount(1)
                .build();

        when(minioService.downloadFile("uploads/single.png"))
                .thenReturn(new java.io.ByteArrayInputStream(new byte[] { 1, 2, 3, 4 }));
        when(aiExtractionService.extractQuestionsFromImageAsJson(anyString(), eq("image/png")))
                .thenReturn("[{\"content\":\"Q1\"}]");

        listener.processUploadedExam(event);

        verify(tikaService, never()).extractText(any());
        verify(aiExtractionService).extractQuestionsFromImageAsJson(anyString(), eq("image/png"));
        verify(eventPublisherService).publishExtractionResult(any());
    }

    @Test
    @DisplayName("Smoke: multi-page PDF concatenates text and extracts once")
    void smoke_multiPagePdf_concatenatesTextAndExtractsOnce() {
        ExamSourceUploadedEvent event = baseEventBuilder()
                .objectKeys(List.of("uploads/page-1.pdf", "uploads/page-2.pdf"))
                .pageCount(2)
                .build();

        when(minioService.downloadFile("uploads/page-1.pdf"))
                .thenReturn(new java.io.ByteArrayInputStream("p1".getBytes()));
        when(minioService.downloadFile("uploads/page-2.pdf"))
                .thenReturn(new java.io.ByteArrayInputStream("p2".getBytes()));
        when(tikaService.extractText(any()))
                .thenReturn("Extracted page one", "Extracted page two");
        when(aiExtractionService.extractQuestionsAsJson(anyString()))
                .thenReturn("[{\"content\":\"Q1\"}]");

        listener.processUploadedExam(event);

        verify(aiExtractionService).extractQuestionsAsJson(anyString());
        verify(eventPublisherService).publishExtractionResult(any());
    }

    @Test
    @DisplayName("Smoke: null event is ignored gracefully")
    void smoke_nullEvent_ignoredGracefully() {
        listener.processUploadedExam(null);
        verifyNoInteractions(minioService, tikaService, aiExtractionService, eventPublisherService);
    }

    private ExamSourceUploadedEvent.ExamSourceUploadedEventBuilder baseEventBuilder() {
        return ExamSourceUploadedEvent.builder()
                .uploadRequestId(999L)
                .examId(888L)
                .originalFileName("test.pdf")
                .fileObjectName("test.pdf")
                .pageCount(1);
    }
}
