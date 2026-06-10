package com.springai.chatsys.dto;

import java.util.List;
import java.util.Map;

public record MysqlDiagnosticsDTO(
        boolean mysqlEnabled,
        boolean mysqlAvailable,
        String userId,
        List<MysqlTableStatusDTO> tables,
        int jsonlKnowledgeRecords,
        int jsonlQuestionRecords,
        Long mysqlKnowledgeDocuments,
        Long mysqlQuestionHistory,
        Long mysqlPermissionRelations,
        Long mysqlAuthUsers,
        Long mysqlAuthSessions,
        Long adminUserCount,
        Map<String, Long> userRoleCount,
        Boolean knowledgeMirrorAligned,
        Boolean questionMirrorAligned,
        String lastError
) {
    public static MysqlDiagnosticsDTO disabled(String userId, int jsonlKnowledgeRecords, int jsonlQuestionRecords) {
        return new MysqlDiagnosticsDTO(
                false,
                false,
                userId,
                List.of(),
                jsonlKnowledgeRecords,
                jsonlQuestionRecords,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                null,
                null,
                ""
        );
    }
}
