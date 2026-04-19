package com.exam_bank.search_service.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@Slf4j
public class TikaService {

    private final Tika tika = new Tika();

    public String extractText(InputStream inputStream) {
        try {
            log.info("Extracting text using Apache Tika...");
            // Giới hạn đọc tối đa (-1 nghĩa là đọc toàn bộ file không giới hạn)
            tika.setMaxStringLength(-1);
            return tika.parseToString(inputStream);
        } catch (Exception e) {
            log.error("Failed to extract text from file: {}", e.getMessage());
            throw new RuntimeException("Text extraction failed", e);
        }
    }
}