package com.springai.chatsys.dto;

import java.util.List;

public record QuestionRecordDTO(
        String id,
        String userId,
        String question,
        String answerPreview,
        String mode,
        String requestType,
        int topK,
        String retrievalScope,
        int retrievedChunkCount,
        List<String> citationChunkIds,
        long elapsedMs,
        String createdAt
) {
    public QuestionRecordDTO {
        retrievalScope = retrievalScope == null || retrievalScope.isBlank() ? "persistent" : retrievalScope;
        citationChunkIds = citationChunkIds == null ? List.of() : citationChunkIds;
    }

    public QuestionRecordDTO(
            String id,
            String userId,
            String question,
            String answerPreview,
            String mode,
            String requestType,
            int topK,
            int retrievedChunkCount,
            List<String> citationChunkIds,
            long elapsedMs,
            String createdAt
    ) {
        this(id, userId, question, answerPreview, mode, requestType, topK, "persistent",
                retrievedChunkCount, citationChunkIds, elapsedMs, createdAt);
    }
}
