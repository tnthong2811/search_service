package com.exam_bank.search_service.service;

import com.exam_bank.search_service.document.ExamDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public List<ExamDocument> searchExams(String keyword, List<String> tags) {
        // Khởi tạo điều kiện bắt buộc: Chỉ lấy đề PUBLISHED
        Criteria criteria = new Criteria("status").is("PUBLISHED");

        // PHẢI GÁN LẠI criteria = criteria.and(...) để lưu trữ điều kiện mới
        if (keyword != null && !keyword.trim().isEmpty()) {
            criteria = criteria.and("title").matches(keyword);
        }

        if (tags != null && !tags.isEmpty()) {
            criteria = criteria.and("tags").in(tags);
        }

        // Build câu lệnh query
        CriteriaQuery query = new CriteriaQuery(criteria);

        // Thực thi tìm kiếm
        SearchHits<ExamDocument> searchHits = elasticsearchOperations.search(query, ExamDocument.class);

        // Trả về danh sách Document
        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }
}