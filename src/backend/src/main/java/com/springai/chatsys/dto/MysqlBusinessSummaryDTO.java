package com.springai.chatsys.dto;

public record MysqlBusinessSummaryDTO(
        boolean mysqlEnabled,
        String userId,
        long totalUsers,
        long totalKnowledgeDocuments,
        long totalIngestHistory,
        long totalQuestionHistory,
        long currentUserKnowledgeDocuments,
        long currentUserIngestHistory,
        long currentUserQuestionHistory,
        boolean currentUserSuggestionCacheReady,
        String lastError
) {
    public static MysqlBusinessSummaryDTO disabled(String userId) {
        return new MysqlBusinessSummaryDTO(
                false,
                userId,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                ""
        );
    }
}
