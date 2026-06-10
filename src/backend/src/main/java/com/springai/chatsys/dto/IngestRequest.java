package com.springai.chatsys.dto;

import jakarta.validation.constraints.NotBlank;

public record IngestRequest(
        String docId,
        String documentName,
        @NotBlank String content,
        String source
) {
}
