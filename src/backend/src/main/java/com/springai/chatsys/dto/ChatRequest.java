package com.springai.chatsys.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String question,
        Integer topK,
        String scope
) {
    public ChatRequest(String question, Integer topK) {
        this(question, topK, null);
    }
}
