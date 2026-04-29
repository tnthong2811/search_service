package com.exam_bank.search_service.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.exam_bank.search_service.document.ExamDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public List<ExamDocument> searchExams(String keyword, List<String> tags) {
        System.out.println("=========================================");
        System.out.println("TRẠM 6 - Dữ liệu thô nhận từ URL:");
        System.out.println("- Keyword: [" + keyword + "]");
        System.out.println("- Tags: " + tags);
        // Khởi tạo BoolQuery Builder (Cấu trúc truy vấn đa điều kiện chuẩn của Elasticsearch)
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // 1. FILTER: Điều kiện bắt buộc - Chỉ lấy đề PUBLISHED
        // Đã sửa thành match() thay vì term() để an toàn tuyệt đối với chữ hoa/chữ thường
        boolBuilder.filter(f -> f.match(m -> m.field("status").query("PUBLISHED")));

        if (keyword != null && !keyword.trim().isEmpty()) {
            boolBuilder.must(m -> m.match(match -> match
                    .field("title")
                    .query(keyword)
                    .operator(Operator.And) // Bắt buộc phải khớp mọi từ (để tránh ra kết quả rác)
                    .fuzziness("2")         // Cho phép sai tối đa 2 ký tự (Cứu được vụ gõ "toans")
            ));
        }

        // 3. FILTER: Lọc theo Tag (Vẫn áp dụng logic chữ thường như chúng ta đã làm)
        if (tags != null && !tags.isEmpty()) {
            List<FieldValue> tagValues = tags.stream()
                    .map(String::toLowerCase)
                    .map(FieldValue::of)
                    .toList();

            boolBuilder.filter(f -> f.terms(t -> t
                    .field("tags")
                    .terms(terms -> terms.value(tagValues))
            ));
        }

        // Đóng gói Query và gửi cho Elasticsearch
        Query esQuery = Query.of(q -> q.bool(boolBuilder.build()));
        System.out.println("TRẠM 8 - Câu lệnh Elasticsearch chuẩn bị chạy:");
        System.out.println(esQuery.toString());
        System.out.println("=========================================");

        // THÊM GIỚI HẠN SỐ LƯỢNG KẾT QUẢ Ở ĐÂY
        NativeQuery nativeQuery = new NativeQueryBuilder()
                .withQuery(esQuery)
                .withPageable(PageRequest.of(0, 50)) // <-- Lấy trang đầu tiên (0) với tối đa 50 kết quả
                .build();

        // Thực thi tìm kiếm
        SearchHits<ExamDocument> searchHits = elasticsearchOperations.search(nativeQuery, ExamDocument.class);

        // Map kết quả trả về
        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }
}