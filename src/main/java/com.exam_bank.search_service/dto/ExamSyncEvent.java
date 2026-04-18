package com.exam_bank.search_service.dto;

import java.util.List;
import lombok.Data;

@Data
public class ExamSyncEvent {
    private Long id;
    private String title;
    private String status; // DRAFT, PUBLISHED, ARCHIVED
    private Boolean isPremium;
    private List<String> tags; // Chỉ cần gửi danh sách tên tag (String) là đủ để search
    private String action; // Giá trị: "UPSERT" (thêm/sửa) hoặc "DELETE" (xóa)
}