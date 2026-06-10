package com.springai.chatsys.dto;

import jakarta.validation.constraints.NotBlank;

public record SearchRequest(
        @NotBlank String question,
        Integer topK,
        String scope
) {
    public SearchRequest(String question, Integer topK) {
        this(question, topK, null);
    }
}
