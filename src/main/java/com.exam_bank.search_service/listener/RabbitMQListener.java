package com.exam_bank.search_service.listener;

import com.exam_bank.search_service.document.ExamDocument;
import com.exam_bank.search_service.dto.ExamSyncEvent;
import com.exam_bank.search_service.repository.ExamSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitMQListener {

    private final ExamSearchRepository examSearchRepository;

    // CHÚ Ý: Đổi tên queue thành queue mới
    @RabbitListener(queues = "${search.sync.queue:search.sync.queue}")
    public void handleExamSync(ExamSyncEvent event) {
        log.info("TRẠM 4 - Đã nhận được Event từ RabbitMQ. Exam ID: {}, Tags: {}", event.getId(), event.getTags());

        try {
            if ("DELETE".equalsIgnoreCase(event.getAction())) {
                examSearchRepository.deleteById(event.getId());
                log.info("Đã xóa Exam ID: {} khỏi Elasticsearch", event.getId());
                return;
            }

            // Trường hợp UPSERT (Thêm hoặc Sửa)
            ExamDocument doc = new ExamDocument();
            doc.setId(event.getId());
            doc.setTitle(event.getTitle());
            doc.setStatus(event.getStatus());
            doc.setIsPremium(event.getIsPremium());
            doc.setTags(event.getTags());

            examSearchRepository.save(doc);
            log.info("Đã đồng bộ thành công Exam ID: {} vào Elasticsearch", event.getId());
        } catch (Exception e) {
            log.error("Lỗi khi đồng bộ Exam ID: {} vào Elasticsearch", event.getId(), e);
        }
    }
}