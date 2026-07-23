package com.afterglow.web;

import com.afterglow.medicaltourism.MedicalTourismClient;
import com.afterglow.notion.NotionClient;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotionClient.NotionApiException.class)
    public ResponseEntity<Map<String, String>> handleNotion(NotionClient.NotionApiException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MedicalTourismClient.MedicalTourismApiException.class)
    public ResponseEntity<Map<String, String>> handleMedicalTourism(
            MedicalTourismClient.MedicalTourismApiException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "접근 권한이 없습니다."));
    }
}
