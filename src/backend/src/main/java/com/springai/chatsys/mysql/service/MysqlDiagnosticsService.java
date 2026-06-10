package com.springai.chatsys.mysql.service;

import com.springai.chatsys.dto.MysqlDiagnosticsDTO;
import com.springai.chatsys.dto.MysqlTableStatusDTO;
import com.springai.chatsys.mysql.config.MysqlProperties;
import com.springai.chatsys.mysql.repository.KnowledgeDocumentRepository;
import com.springai.chatsys.mysql.repository.QuestionHistoryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
@ConditionalOnProperty(prefix = "mysql", name = "enabled", havingValue = "true")
public class MysqlDiagnosticsService {

    private final MysqlProperties properties;
    private final DataSource dataSource;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final QuestionHistoryRepository questionHistoryRepository;
    private final Optional<MysqlAuthService> mysqlAuthService;
    private final Optional<MysqlPermissionService> mysqlPermissionService;
    private volatile String lastError;

    public MysqlDiagnosticsService(
            MysqlProperties properties,
            DataSource dataSource,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            QuestionHistoryRepository questionHistoryRepository,
            Optional<MysqlAuthService> mysqlAuthService,
            Optional<MysqlPermissionService> mysqlPermissionService
    ) {
        this.properties = properties;
        this.dataSource = dataSource;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.questionHistoryRepository = questionHistoryRepository;
        this.mysqlAuthService = mysqlAuthService;
        this.mysqlPermissionService = mysqlPermissionService;
    }

    public MysqlDiagnosticsDTO diagnosticsForUser(String userKey, int jsonlKnowledgeRecords, int jsonlQuestionRecords) {
        try (Connection connection = dataSource.getConnection()) {
            boolean available = connection.isValid(1);
            List<MysqlTableStatusDTO> tables = inspectTables(connection);
            Long mysqlKnowledge = knowledgeDocumentRepository.countByUserKey(userKey);
            Long mysqlQuestions = questionHistoryRepository.countByUserKey(userKey);
            Long mysqlPermissions = mysqlPermissionService.map(service -> service.countForUser(userKey)).orElse(null);
            Long mysqlAuthUsers = mysqlAuthService.map(MysqlAuthService::totalUsers).orElse(null);
            Long mysqlAuthSessions = mysqlAuthService.map(MysqlAuthService::totalSessions).orElse(null);
            Long adminUserCount = mysqlAuthService.map(MysqlAuthService::adminUserCount).orElse(null);
            Map<String, Long> userRoleCount = mysqlAuthService.map(MysqlAuthService::userRoleCount).orElse(Map.of());
            lastError = null;
            return new MysqlDiagnosticsDTO(
                    properties.isEnabled(),
                    available,
                    userKey,
                    tables,
                    jsonlKnowledgeRecords,
                    jsonlQuestionRecords,
                    mysqlKnowledge,
                    mysqlQuestions,
                    mysqlPermissions,
                    mysqlAuthUsers,
                    mysqlAuthSessions,
                    adminUserCount,
                    userRoleCount,
                    jsonlKnowledgeRecords == mysqlKnowledge.intValue(),
                    jsonlQuestionRecords == mysqlQuestions.intValue(),
                    ""
            );
        } catch (Exception ex) {
            recordFailure(ex);
            return new MysqlDiagnosticsDTO(
                    properties.isEnabled(),
                    false,
                    userKey,
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
                    lastError
            );
        }
    }

    public boolean tableReady(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            Set<String> columns = loadColumns(connection).get(normalize(tableName));
            if (columns == null) {
                return false;
            }
            TableSpec spec = expectedTables().get(normalize(tableName));
            return spec == null || columns.containsAll(spec.columns());
        } catch (Exception ex) {
            recordFailure(ex);
            return false;
        }
    }

    public String lastError() {
        return lastError;
    }

    public void recordFailure(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            lastError = ex.getClass().getSimpleName();
            return;
        }
        lastError = message.length() > 240 ? message.substring(0, 240) + "..." : message;
    }

    private List<MysqlTableStatusDTO> inspectTables(Connection connection) throws Exception {
        Map<String, TableSpec> expected = expectedTables();
        Map<String, Set<String>> actual = loadColumns(connection);
        List<MysqlTableStatusDTO> result = new ArrayList<>();
        expected.forEach((tableName, spec) -> {
            Set<String> columns = actual.get(tableName);
            boolean present = columns != null;
            List<String> missingColumns = spec.columns().stream()
                    .filter(column -> columns == null || !columns.contains(column))
                    .toList();
            result.add(new MysqlTableStatusDTO(tableName, spec.required(), present, missingColumns));
        });
        return result;
    }

    private Map<String, Set<String>> loadColumns(Connection connection) throws Exception {
        String sql = """
                select table_name, column_name
                from information_schema.columns
                where table_schema = ?
                """;
        Map<String, Set<String>> columns = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, properties.getDatabase());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String table = normalize(resultSet.getString("table_name"));
                    String column = normalize(resultSet.getString("column_name"));
                    columns.computeIfAbsent(table, ignored -> new TreeSet<>()).add(column);
                }
            }
        }
        return columns;
    }

    private Map<String, TableSpec> expectedTables() {
        Map<String, TableSpec> tables = new LinkedHashMap<>();
        tables.put("demo_user", new TableSpec(true, List.of("id", "demo_user_key", "display_name", "created_at", "updated_at")));
        tables.put("knowledge_document", new TableSpec(true, List.of("id", "user_key", "doc_id", "document_name", "source", "storage_scope", "vector_store_mode", "chunk_count", "embedding_dimensions", "content_preview", "created_at", "updated_at")));
        tables.put("ingest_history", new TableSpec(true, List.of("id", "user_key", "doc_id", "document_name", "source", "vector_store_mode", "chunk_count", "embedding_dimensions", "content_preview", "created_at")));
        tables.put("question_history", new TableSpec(true, List.of("id", "user_key", "question", "answer_preview", "mode", "request_type", "top_k", "retrieval_scope", "retrieved_chunk_count", "citation_chunk_ids_json", "elapsed_ms", "created_at")));
        tables.put("user_suggestion_cache", new TableSpec(true, List.of("id", "user_key", "suggestions_json", "knowledge_version", "created_at", "updated_at")));
        tables.put("knowledge_permission", new TableSpec(false, List.of("id", "user_key", "doc_id", "permission_type", "created_at")));
        tables.put("app_user", new TableSpec(false, List.of("id", "user_key", "username", "password_hash", "auth_provider", "role", "enabled", "created_at", "updated_at")));
        tables.put("auth_session", new TableSpec(false, List.of("id", "token_hash", "user_key", "username", "role", "revoked", "expires_at", "created_at")));
        return tables;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record TableSpec(boolean required, List<String> columns) {
    }
}
