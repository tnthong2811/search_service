package com.exam_bank.search_service.service;

import com.exam_bank.search_service.dto.AiExtractionResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${exam.events.exchange}")
    private String exchange;

    @Value("${search.events.ai-extracted-routing-key}")
    private String aiExtractedRoutingKey;

    public void publishExtractionResult(AiExtractionResultEvent event) {
        log.info("Publishing AI extraction result to exam_service. Exam ID: {}, Success: {}",
                event.getExamId(), event.getSuccessFlag());

        // Ném gói quà chứa JSON lên băng chuyền RabbitMQ để exam_service nhận
        rabbitTemplate.convertAndSend(exchange, aiExtractedRoutingKey, event);
    }
}