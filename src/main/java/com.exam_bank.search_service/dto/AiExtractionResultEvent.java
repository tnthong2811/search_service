package com.exam_bank.search_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiExtractionResultEvent {
    private Long examId;
    private String uploadedByUserId; // Để biết ai là người up đề
    private String aiJsonResult;     // Chứa nguyên mảng JSON kết quả
    private Boolean successFlag;      // Đánh dấu thành công hay thất bại
    private String errorMessage;     // Chứa câu báo lỗi (nếu có)
}