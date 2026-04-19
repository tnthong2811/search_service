package com.exam_bank.search_service.listener;

import com.exam_bank.search_service.dto.AiExtractionResultEvent;
import com.exam_bank.search_service.dto.ExamSourceUploadedEvent;
import com.exam_bank.search_service.service.AiExtractionService;
import com.exam_bank.search_service.service.EventPublisherService;
import com.exam_bank.search_service.service.MinioService;
import com.exam_bank.search_service.service.TikaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Base64;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiWorkerListener {

    private static final int MAX_ERROR_MSG_LENGTH = 1000;

    private final MinioService minioService;
    private final TikaService tikaService;
    private final AiExtractionService aiExtractionService;
    private final EventPublisherService eventPublisherService;

    @RabbitListener(queues = "${search.events.queue}")
    public void processUploadedExam(ExamSourceUploadedEvent event) {
        if (event == null) {
            log.warn("Received null ExamSourceUploadedEvent, skipping");
            return;
        }

        Long uploadRequestId = event.getUploadRequestId();
        Long examId = event.getExamId();
        Integer pageCount = event.getPageCount();

        if (uploadRequestId != null) {
            MDC.put("uploadRequestId", String.valueOf(uploadRequestId));
        }
        if (examId != null) {
            MDC.put("examId", String.valueOf(examId));
        }
        if (pageCount != null) {
            MDC.put("pageCount", String.valueOf(pageCount));
        }

        try {
            log.info("Processing extraction for uploadRequestId={} examId={} pageCount={}",
                    uploadRequestId, examId, pageCount);

            String aiJsonResult = runExtraction(event);

            log.info("Extraction succeeded: uploadRequestId={} examId={}", uploadRequestId, examId);

            AiExtractionResultEvent successEvent = AiExtractionResultEvent.builder()
                    .examId(examId)
                    .uploadedByUserId(event.getUploadedByUserId())
                    .aiJsonResult(aiJsonResult)
                    .successFlag(true)
                    .errorMessage(null)
                    .uploadRequestId(uploadRequestId)
                    .pageCount(pageCount)
                    .timestamp(System.currentTimeMillis())
                    .build();
            eventPublisherService.publishExtractionResult(successEvent);

        } catch (Exception ex) {
            String errorMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (errorMessage.length() > MAX_ERROR_MSG_LENGTH) {
                errorMessage = errorMessage.substring(0, MAX_ERROR_MSG_LENGTH);
            }
            log.error("Extraction failed for uploadRequestId={} examId={}: {}", uploadRequestId, examId,
                    errorMessage, ex);

            AiExtractionResultEvent failedEvent = AiExtractionResultEvent.builder()
                    .examId(examId)
                    .uploadedByUserId(event.getUploadedByUserId())
                    .aiJsonResult(null)
                    .successFlag(false)
                    .errorMessage(errorMessage)
                    .uploadRequestId(uploadRequestId)
                    .pageCount(pageCount)
                    .timestamp(System.currentTimeMillis())
                    .build();
            try {
                eventPublisherService.publishExtractionResult(failedEvent);
            } catch (Exception publishEx) {
                log.error("Failed to publish failure event for uploadRequestId={}: {}",
                        uploadRequestId, publishEx.getMessage(), publishEx);
            }
        } finally {
            MDC.remove("uploadRequestId");
            MDC.remove("examId");
            MDC.remove("pageCount");
            MDC.remove("currentPageIndex");
        }
    }

    private String runExtraction(ExamSourceUploadedEvent event) throws Exception {
        List<String> objectKeys = event.getObjectKeys();
        if (objectKeys != null && !objectKeys.isEmpty()) {
            String first = trimOrNull(objectKeys.get(0));
            if (first == null) {
                throw new IllegalArgumentException("First object key is blank");
            }
            if (isImage(first)) {
                if (objectKeys.size() > 1) {
                    throw new UnsupportedOperationException("Multi-page image not supported");
                }
                return extractImage(first);
            }
            return extractDocumentMulti(objectKeys);
        }

        // Fallback: single fileObjectName
        String fileObjectName = trimOrNull(event.getFileObjectName());
        if (fileObjectName == null) {
            throw new IllegalArgumentException("No object key provided");
        }
        if (isImage(fileObjectName)) {
            return extractImage(fileObjectName);
        }
        return extractDocumentSingle(fileObjectName);
    }

    private String extractImage(String objectKey) throws Exception {
        log.info("Detected IMAGE file {}, bypassing Tika and using Gemini Vision OCR", objectKey);
        byte[] imageBytes;
        try (InputStream fileStream = minioService.downloadFile(objectKey)) {
            imageBytes = fileStream.readAllBytes();
        }
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String mimeType = objectKey.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
        return aiExtractionService.extractQuestionsFromImageAsJson(base64Image, mimeType);
    }

    private String extractDocumentSingle(String objectKey) throws Exception {
        log.info("Detected DOCUMENT file {}, extracting via Tika", objectKey);
        String rawText;
        try (InputStream fileStream = minioService.downloadFile(objectKey)) {
            rawText = tikaService.extractText(fileStream);
        }
        if (rawText == null || rawText.trim().isEmpty()) {
            throw new IllegalStateException("Extracted text is empty. File might be unreadable or blank.");
        }
        return aiExtractionService.extractQuestionsAsJson(rawText);
    }

    private String extractDocumentMulti(List<String> objectKeys) throws Exception {
        log.info("Detected MULTI-PAGE DOCUMENT ({} pages), extracting via Tika then concatenating",
                objectKeys.size());
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < objectKeys.size(); i++) {
            String key = trimOrNull(objectKeys.get(i));
            if (key == null) {
                log.warn("Skipping blank object key at index {}", i);
                continue;
            }
            int pageIndex = i + 1;
            MDC.put("currentPageIndex", String.valueOf(pageIndex));
            try {
                String rawText;
                try (InputStream fileStream = minioService.downloadFile(key)) {
                    rawText = tikaService.extractText(fileStream);
                }
                if (rawText != null && !rawText.trim().isEmpty()) {
                    if (combined.length() > 0) {
                        combined.append("\n\n");
                    }
                    combined.append(String.format("--- Page %d ---", pageIndex))
                            .append("\n\n")
                            .append(rawText);
                }
            } finally {
                MDC.remove("currentPageIndex");
            }
        }
        if (combined.length() == 0) {
            throw new IllegalStateException("Extracted text is empty across all pages");
        }
        return aiExtractionService.extractQuestionsAsJson(combined.toString());
    }

    private boolean isImage(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
