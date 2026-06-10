package com.springai.chatsys.exception;

import com.springai.chatsys.auth.AuthAccessDeniedException;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "VALIDATION_ERROR",
                "message", ex.getBindingResult().getAllErrors().get(0).getDefaultMessage()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "BAD_REQUEST",
                "message", ex.getMessage() == null ? "Bad request" : ex.getMessage()
        ));
    }

    @ExceptionHandler(NonTransientAiException.class)
    public ResponseEntity<Map<String, Object>> handleAiProviderError(NonTransientAiException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "AI_PROVIDER_ERROR",
                "message", ex.getMessage() == null ? "AI provider request failed" : ex.getMessage()
        ));
    }

    @ExceptionHandler(AuthAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AuthAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "FORBIDDEN",
                "message", ex.getMessage() == null ? "Forbidden" : ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "SERVICE_UNAVAILABLE",
                "message", ex.getMessage() == null ? "Service is not available" : ex.getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", ex.getMessage() == null ? "Unexpected server error" : ex.getMessage()
        ));
    }
}
