package com.springai.chatsys.dto;

import java.util.List;

public record SearchResponse(
        String question,
        int topK,
        List<RetrievedChunkDTO> retrievedChunks,
        long elapsedMs
) {
}
