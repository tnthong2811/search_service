package com.exam_bank.search_service.repository;

import com.exam_bank.search_service.document.ExamDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamSearchRepository extends ElasticsearchRepository<ExamDocument, Long> {
}