package com.exam_bank.search_service.listener;

import com.exam_bank.search_service.dto.AiExtractionResultEvent;
import com.exam_bank.search_service.dto.ExamSourceUploadedEvent;
import com.exam_bank.search_service.service.AiExtractionService;
import com.exam_bank.search_service.service.EventPublisherService;
import com.exam_bank.search_service.service.MinioService;
import com.exam_bank.search_service.service.TikaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiWorkerListener {

    private final MinioService minioService;
    private final TikaService tikaService;
    private final AiExtractionService aiExtractionService;
    private final EventPublisherService eventPublisherService; // Đã thêm publisher

    @RabbitListener(queues = "${search.events.queue}")
    public void processUploadedExam(ExamSourceUploadedEvent event) {
        log.info("=================================================");
        log.info("Received new file for AI Extraction. Exam ID: {}", event.getExamId());

        try {
            InputStream fileStream = minioService.downloadFile(event.getFileObjectName());
            String fileName = event.getFileObjectName().toLowerCase();
            String aiJsonResult;

            // KIỂM TRA ĐỊNH DẠNG FILE TỪ TÊN FILE ĐỂ QUYẾT ĐỊNH LUỒNG
            if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                log.info("Detected IMAGE file. Bypassing Tika, using Gemini Vision OCR...");

                byte[] imageBytes = fileStream.readAllBytes();
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                String mimeType = fileName.endsWith(".png") ? "image/png" : "image/jpeg";
                fileStream.close();

                // Gửi thẳng file ảnh dạng Base64 cho Gemini
                aiJsonResult = aiExtractionService.extractQuestionsFromImageAsJson(base64Image, mimeType);

            } else {
                log.info("Detected DOCUMENT file. Using Apache Tika...");

                String rawText = tikaService.extractText(fileStream);
                fileStream.close();

                if (rawText == null || rawText.trim().isEmpty()) {
                    throw new RuntimeException("Extracted text is empty. File might be an unreadable scanned image or blank.");
                }

                // Gửi text bóc được cho Gemini
                aiJsonResult = aiExtractionService.extractQuestionsAsJson(rawText);
            }

            log.info("Successfully extracted questions for Exam ID: {}.", event.getExamId());

            // In gọn log preview thay vì in hết
            if (aiJsonResult != null) {
                log.info("Full Result: \n{}", aiJsonResult);
            }

            // BẮN SỰ KIỆN THÀNH CÔNG VỀ CHO EXAM_SERVICE
            AiExtractionResultEvent successEvent = AiExtractionResultEvent.builder()
                    .examId(event.getExamId())
                    .uploadedByUserId(event.getUploadedByUserId())
                    .aiJsonResult(aiJsonResult)
                    .successFlag(true)
                    .build();
            eventPublisherService.publishExtractionResult(successEvent);

        } catch (Exception e) {
            log.error("Failed to process exam AI extraction for Exam ID: {}. Error: {}", event.getExamId(), e.getMessage());

            // BẮN SỰ KIỆN THẤT BẠI VỀ CHO EXAM_SERVICE
            AiExtractionResultEvent failedEvent = AiExtractionResultEvent.builder()
                    .examId(event.getExamId())
                    .uploadedByUserId(event.getUploadedByUserId())
                    .successFlag(false)
                    .errorMessage(e.getMessage())
                    .build();
            eventPublisherService.publishExtractionResult(failedEvent);
        }
        log.info("=================================================");
    }
}