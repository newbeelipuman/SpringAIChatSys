package com.springai.chatsys.dto;

public record KnowledgeRecordDTO(
        String id,
        String userId,
        String docId,
        String documentName,
        String source,
        String vectorStoreMode,
        int chunkCount,
        int embeddingDimensions,
        String contentPreview,
        String createdAt
) {
}
