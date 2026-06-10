package com.springai.chatsys.dto;

public record IngestResponse(
        String docId,
        String documentName,
        int chunkCount,
        int embeddingDimensions,
        String vectorStoreMode,
        long elapsedMs
) {
}
