package com.exam_bank.search_service.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;
import lombok.Data;

@Data
@Document(indexName = "exams")
public class ExamDocument {

    @Id
    private Long id;

    // FieldType.Text giúp tokenize các từ để search Full-text (vd: gõ "Toán" ra "Đề thi Toán học")
    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String searchTitle;

    // FieldType.Keyword dùng để filter chính xác 100%, không cắt chữ
    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Boolean)
    private Boolean isPremium;

    // Lưu mảng tên các tags, dạng Keyword để filter
    @Field(type = FieldType.Keyword)
    private List<String> tags;
}