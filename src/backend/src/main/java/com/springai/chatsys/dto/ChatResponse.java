package com.springai.chatsys.dto;

import java.util.List;

public record ChatResponse(
        String question,
        String answer,
        List<CitationDTO> citations,
        List<RetrievedChunkDTO> retrievedChunks,
        long elapsedMs,
        String mode
) {
}
