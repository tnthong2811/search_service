package com.exam_bank.search_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSourceUploadedEvent {
    private Long examId;
    private String fileObjectName;
    private String originalFileName;
    private String uploadedByUserId;
}