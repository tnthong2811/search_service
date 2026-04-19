package com.exam_bank.search_service.controller;

import com.exam_bank.search_service.document.ExamDocument;
import com.exam_bank.search_service.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    // Endpoint: GET /api/v1/search/exams?keyword=Toan&tags=Toan12&tags=HinhHoc
    @GetMapping("/exams")
    public ResponseEntity<List<ExamDocument>> searchExams(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> tags) {

        List<ExamDocument> results = searchService.searchExams(keyword, tags);
        return ResponseEntity.ok(results);
    }
}