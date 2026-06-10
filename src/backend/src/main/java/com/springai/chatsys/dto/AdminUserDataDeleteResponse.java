package com.springai.chatsys.dto;

public record AdminUserDataDeleteResponse(
        String userKey,
        int clearedKnowledgeRecords,
        int clearedQuestionRecords,
        int clearedTemporaryChunks
) {
}
