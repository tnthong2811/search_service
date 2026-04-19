package com.exam_bank.search_service.listener;

import com.exam_bank.search_service.dto.AiExtractionResultEvent;
import com.exam_bank.search_service.dto.ExamSourceUploadedEvent;
import com.exam_bank.search_service.service.AiExtractionService;
import com.exam_bank.search_service.service.EventPublisherService;
import com.exam_bank.search_service.service.MinioService;
import com.exam_bank.search_service.service.TikaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiWorkerListener unit tests")
class AiWorkerListenerTest {

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
    void processUploadedExam_whenMultiPageDocument_thenConcatenateAndExtractOnce() {
        ExamSourceUploadedEvent event = baseEventBuilder()
                .objectKeys(List.of("uploads/page-1.pdf", "uploads/page-2.pdf"))
                .pageCount(2)
                .build();

        when(minioService.downloadFile("uploads/page-1.pdf"))
                .thenReturn(stream("p1"));
        when(minioService.downloadFile("uploads/page-2.pdf"))
                .thenReturn(stream("p2"));
        when(tikaService.extractText(any(InputStream.class)))
                .thenReturn("Extracted page one", "Extracted page two");
        when(aiExtractionService.extractQuestionsAsJson(anyString()))
                .thenReturn("[{\"content\":\"Q1\"}]");

        listener.processUploadedExam(event);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiExtractionService).extractQuestionsAsJson(textCaptor.capture());
        String merged = textCaptor.getValue();
        assertThat(merged)
                .contains("--- Page 1 ---")
                .contains("--- Page 2 ---")
                .contains("Extracted page one")
                .contains("Extracted page two");

        verify(aiExtractionService, never()).extractQuestionsFromImageAsJson(anyString(), anyString());
        verify(eventPublisherService).publishExtractionResult(any(AiExtractionResultEvent.class));
    }

    @Test
    void processUploadedExam_whenSinglePageImage_thenUseVisionPathAndPublishSuccess() {
        ExamSourceUploadedEvent event = baseEventBuilder()
                .objectKeys(List.of("uploads/single.png"))
                .pageCount(1)
                .build();

        when(minioService.downloadFile("uploads/single.png"))
                .thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 }));
        when(aiExtractionService.extractQuestionsFromImageAsJson(anyString(), eq("image/png")))
                .thenReturn("[{\"content\":\"From image\"}]");

        listener.processUploadedExam(event);

        verify(tikaService, never()).extractText(any(InputStream.class));
        verify(aiExtractionService).extractQuestionsFromImageAsJson(anyString(), eq("image/png"));

        ArgumentCaptor<AiExtractionResultEvent> eventCaptor = ArgumentCaptor.forClass(AiExtractionResultEvent.class);
        verify(eventPublisherService).publishExtractionResult(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getSuccessFlag()).isTrue();
    }

    @Test
    void processUploadedExam_whenMultiPageImage_thenPublishFailureEvent() {
        ExamSourceUploadedEvent event = baseEventBuilder()
                .objectKeys(List.of("uploads/1.png", "uploads/2.png"))
                .pageCount(2)
                .build();

        listener.processUploadedExam(event);

        ArgumentCaptor<AiExtractionResultEvent> eventCaptor = ArgumentCaptor.forClass(AiExtractionResultEvent.class);
        verify(eventPublisherService).publishExtractionResult(eventCaptor.capture());

        AiExtractionResultEvent published = eventCaptor.getValue();
        assertThat(published.getSuccessFlag()).isFalse();
        assertThat(published.getErrorMessage()).contains("Multi-page image not supported");
        assertThat(published.getUploadRequestId()).isEqualTo(777L);
        assertThat(published.getPageCount()).isEqualTo(2);

        verify(minioService, never()).downloadFile(anyString());
        verify(aiExtractionService, never()).extractQuestionsFromImageAsJson(anyString(), anyString());
        verify(aiExtractionService, never()).extractQuestionsAsJson(anyString());
    }

    @Test
    void processUploadedExam_whenObjectKeysNull_thenFallbackToFileObjectName() {
        ExamSourceUploadedEvent event = baseEventBuilder()
                .objectKeys(null)
                .fileObjectName("legacy.pdf")
                .build();

        when(minioService.downloadFile("legacy.pdf"))
                .thenReturn(stream("legacy-content"));
        when(tikaService.extractText(any(InputStream.class)))
                .thenReturn("legacy text");
        when(aiExtractionService.extractQuestionsAsJson("legacy text"))
                .thenReturn("[{\"content\":\"Q legacy\"}]");

        listener.processUploadedExam(event);

        verify(minioService).downloadFile("legacy.pdf");
        verify(aiExtractionService).extractQuestionsAsJson("legacy text");
        verify(eventPublisherService).publishExtractionResult(any(AiExtractionResultEvent.class));
    }

    @Test
    void processUploadedExam_whenObjectKeysEmpty_thenFallbackToFileObjectName() {
        ExamSourceUploadedEvent event = baseEventBuilder()
                .objectKeys(List.of())
                .fileObjectName("legacy-empty.pdf")
                .build();

        when(minioService.downloadFile("legacy-empty.pdf"))
                .thenReturn(stream("legacy-empty-content"));
        when(tikaService.extractText(any(InputStream.class)))
                .thenReturn("legacy empty text");
        when(aiExtractionService.extractQuestionsAsJson("legacy empty text"))
                .thenReturn("[{\"content\":\"Q legacy empty\"}]");

        listener.processUploadedExam(event);

        verify(minioService).downloadFile("legacy-empty.pdf");
        verify(aiExtractionService).extractQuestionsAsJson("legacy empty text");
        verify(eventPublisherService).publishExtractionResult(any(AiExtractionResultEvent.class));
    }

    @Test
    void processUploadedExam_whenEventNull_thenSkipWithoutPublish() {
        listener.processUploadedExam(null);

        verifyNoInteractions(minioService, tikaService, aiExtractionService, eventPublisherService);
    }

    @Test
    void processUploadedExam_whenTikaThrows_thenPublishFailureWithErrorMessage() {
        ExamSourceUploadedEvent event = baseEventBuilder()
                .objectKeys(null)
                .fileObjectName("broken.pdf")
                .build();

        when(minioService.downloadFile("broken.pdf"))
                .thenReturn(stream("broken"));
        when(tikaService.extractText(any(InputStream.class)))
                .thenThrow(new RuntimeException("Tika exploded"));

        listener.processUploadedExam(event);

        ArgumentCaptor<AiExtractionResultEvent> eventCaptor = ArgumentCaptor.forClass(AiExtractionResultEvent.class);
        verify(eventPublisherService).publishExtractionResult(eventCaptor.capture());

        AiExtractionResultEvent published = eventCaptor.getValue();
        assertThat(published.getSuccessFlag()).isFalse();
        assertThat(published.getErrorMessage()).contains("Tika exploded");
        assertThat(published.getTimestamp()).isNotNull();
    }

    @Test
    void processUploadedExam_whenGeminiThrows_thenPublishFailureEvent() {
        ExamSourceUploadedEvent event = baseEventBuilder()
                .objectKeys(null)
                .fileObjectName("gemini-fail.pdf")
                .build();

        when(minioService.downloadFile("gemini-fail.pdf"))
                .thenReturn(stream("ok"));
        when(tikaService.extractText(any(InputStream.class)))
                .thenReturn("parsed text");
        when(aiExtractionService.extractQuestionsAsJson("parsed text"))
                .thenThrow(new RuntimeException("Gemini timeout"));

        listener.processUploadedExam(event);

        ArgumentCaptor<AiExtractionResultEvent> eventCaptor = ArgumentCaptor.forClass(AiExtractionResultEvent.class);
        verify(eventPublisherService).publishExtractionResult(eventCaptor.capture());
        AiExtractionResultEvent published = eventCaptor.getValue();
        assertThat(published.getSuccessFlag()).isFalse();
        assertThat(published.getErrorMessage()).contains("Gemini timeout");
    }

    @Test
    void processUploadedExam_whenSuccess_thenPublishAllContractFields() {
        ExamSourceUploadedEvent event = baseEventBuilder()
                .examId(123L)
                .uploadedByUserId("uploader-42")
                .uploadRequestId(456L)
                .pageCount(3)
                .fileObjectName("ok.pdf")
                .objectKeys(null)
                .build();

        when(minioService.downloadFile("ok.pdf"))
                .thenReturn(stream("content"));
        when(tikaService.extractText(any(InputStream.class)))
                .thenReturn("normalized text");
        when(aiExtractionService.extractQuestionsAsJson("normalized text"))
                .thenReturn("[{\"content\":\"Q\"}]");

        listener.processUploadedExam(event);

        ArgumentCaptor<AiExtractionResultEvent> eventCaptor = ArgumentCaptor.forClass(AiExtractionResultEvent.class);
        verify(eventPublisherService).publishExtractionResult(eventCaptor.capture());

        AiExtractionResultEvent published = eventCaptor.getValue();
        assertThat(published.getExamId()).isEqualTo(123L);
        assertThat(published.getUploadedByUserId()).isEqualTo("uploader-42");
        assertThat(published.getAiJsonResult()).isEqualTo("[{\"content\":\"Q\"}]");
        assertThat(published.getSuccessFlag()).isTrue();
        assertThat(published.getErrorMessage()).isNull();
        assertThat(published.getUploadRequestId()).isEqualTo(456L);
        assertThat(published.getPageCount()).isEqualTo(3);
        assertThat(published.getTimestamp()).isNotNull();
    }

    private ExamSourceUploadedEvent.ExamSourceUploadedEventBuilder baseEventBuilder() {
        return ExamSourceUploadedEvent.builder()
                .examId(88L)
                .fileObjectName("legacy.pdf")
                .originalFileName("legacy.pdf")
                .uploadedByUserId("99")
                .pageCount(1)
                .uploadRequestId(777L);
    }

    private InputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
