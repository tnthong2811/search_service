package com.exam_bank.search_service.dto;

import java.util.ArrayList;
import java.util.List;

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

    /** Multi-page valet-key upload support */
    @Builder.Default
    private List<String> objectKeys = new ArrayList<>();
    private Integer pageCount;
    private Long uploadRequestId;
}
