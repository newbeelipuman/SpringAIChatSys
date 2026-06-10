package com.springai.chatsys.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.springai.chatsys.dto.AskResponse;
import com.springai.chatsys.dto.ChatResponse;
import com.springai.chatsys.dto.IngestMaterialsResponse;
import com.springai.chatsys.dto.IngestRequest;
import com.springai.chatsys.dto.IngestResponse;
import com.springai.chatsys.dto.KnowledgeRecordDTO;
import com.springai.chatsys.dto.QuestionRecordDTO;
import com.springai.chatsys.dto.SearchResponse;
import com.springai.chatsys.mysql.config.MysqlProperties;
import com.springai.chatsys.mysql.service.MysqlHistoryMirrorService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Primary
@EnableConfigurationProperties(MysqlProperties.class)
public class DemoAuditService implements DemoHistoryStore {

    private static final Logger log = LoggerFactory.getLogger(DemoAuditService.class);
    private static final int PREVIEW_LIMIT = 240;
    private static final String TYPE_KNOWLEDGE = "knowledge";
    private static final String TYPE_QUESTION = "question";
    private static final String TYPE_CLEAR_USER_KNOWLEDGE = "clear-user-knowledge";
    private static final String TYPE_CLEAR_USER_QUESTIONS = "clear-user-questions";
    private static final String TYPE_CLEAR_USER_KNOWLEDGE_RECORDS = "clear-user-knowledge-records";
    private static final String TYPE_CLEAR_USER_QUESTION_RECORDS = "clear-user-question-records";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Path auditPath = Path.of(System.getProperty("user.dir"), "data", "demo-audit-log.jsonl");
    private final List<KnowledgeRecordDTO> knowledgeRecords = new CopyOnWriteArrayList<>();
    private final List<QuestionRecordDTO> questionRecords = new CopyOnWriteArrayList<>();
    private final Optional<MysqlHistoryMirrorService> mysqlHistoryMirrorService;
    private final MysqlProperties mysqlProperties;

    public DemoAuditService(Optional<MysqlHistoryMirrorService> mysqlHistoryMirrorService, MysqlProperties mysqlProperties) {
        this.mysqlHistoryMirrorService = mysqlHistoryMirrorService;
        this.mysqlProperties = mysqlProperties;
    }

    @PostConstruct
    public void load() {
        if (!Files.exists(auditPath)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(auditPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    loadLine(line);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load demo audit log: " + auditPath, ex);
        }
    }

    public void recordIngest(String userId, IngestRequest request, IngestResponse response) {
        KnowledgeRecordDTO record = new KnowledgeRecordDTO(
                newId(),
                DemoUserContext.resolve(userId),
                response.docId(),
                response.documentName(),
                valueOrDefault(request.source(), response.documentName()),
                response.vectorStoreMode(),
                response.chunkCount(),
                response.embeddingDimensions(),
                preview(request.content()),
                Instant.now().toString()
        );
        appendKnowledge(record);
    }

    public void recordMaterialsIngest(String userId, IngestMaterialsResponse response) {
        String resolvedUser = DemoUserContext.resolve(userId);
        response.items().stream()
                .filter(item -> item.ingest() != null)
                .forEach(item -> appendKnowledge(new KnowledgeRecordDTO(
                        newId(),
                        resolvedUser,
                        item.ingest().docId(),
                        item.ingest().documentName(),
                        item.filename(),
                        item.ingest().vectorStoreMode(),
                        item.ingest().chunkCount(),
                        item.ingest().embeddingDimensions(),
                        "内置演示素材：" + item.filename(),
                        Instant.now().toString()
                )));
    }

    public void recordChat(String userId, ChatResponse response) {
        recordChat(userId, response, RetrievalScope.PERSISTENT.wireValue());
    }

    public void recordChat(String userId, ChatResponse response, String retrievalScope) {
        appendQuestion(new QuestionRecordDTO(
                newId(),
                DemoUserContext.resolve(userId),
                response.question(),
                preview(response.answer()),
                response.mode(),
                "rag-chat",
                response.retrievedChunks().size(),
                normalizedRetrievalScope(retrievalScope),
                response.retrievedChunks().size(),
                response.citations().stream().map(citation -> citation.chunkId()).toList(),
                response.elapsedMs(),
                Instant.now().toString()
        ));
    }

    public void recordSearch(String userId, SearchResponse response) {
        recordSearch(userId, response, RetrievalScope.PERSISTENT.wireValue());
    }

    public void recordSearch(String userId, SearchResponse response, String retrievalScope) {
        appendQuestion(new QuestionRecordDTO(
                newId(),
                DemoUserContext.resolve(userId),
                response.question(),
                "",
                "vector-search",
                "search",
                response.topK(),
                normalizedRetrievalScope(retrievalScope),
                response.retrievedChunks().size(),
                response.retrievedChunks().stream().map(chunk -> chunk.chunkId()).toList(),
                response.elapsedMs(),
                Instant.now().toString()
        ));
    }

    public void recordDirectAsk(String userId, String question, AskResponse response) {
        appendQuestion(new QuestionRecordDTO(
                newId(),
                DemoUserContext.resolve(userId),
                question,
                preview(response.answer()),
                response.mode(),
                "direct-ask",
                0,
                "none",
                0,
                List.of(),
                response.elapsedMs(),
                Instant.now().toString()
        ));
    }

    public List<KnowledgeRecordDTO> knowledgeForUser(String userId) {
        String resolvedUser = DemoUserContext.resolve(userId);
        Optional<List<KnowledgeRecordDTO>> mysqlRecords = readKnowledgeFromMysql(resolvedUser);
        if (mysqlRecords.isPresent()) {
            return mysqlRecords.get();
        }
        return newestFirst(knowledgeRecords.stream()
                .filter(record -> record.userId().equals(resolvedUser))
                .toList());
    }

    public List<QuestionRecordDTO> questionsForUser(String userId) {
        String resolvedUser = DemoUserContext.resolve(userId);
        Optional<List<QuestionRecordDTO>> mysqlRecords = readQuestionsFromMysql(resolvedUser);
        if (mysqlRecords.isPresent()) {
            return mysqlRecords.get();
        }
        return newestFirst(questionRecords.stream()
                .filter(record -> record.userId().equals(resolvedUser))
                .toList());
    }

    public List<QuestionRecordDTO> questionsForUser(String userId, List<String> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }
        return newestFirst(questionsForUser(userId).stream()
                .filter(record -> questionIds.contains(record.id()))
                .toList());
    }

    public int jsonlKnowledgeCountForUser(String userId) {
        String resolvedUser = DemoUserContext.resolve(userId);
        return (int) knowledgeRecords.stream()
                .filter(record -> record.userId().equals(resolvedUser))
                .count();
    }

    public int jsonlQuestionCountForUser(String userId) {
        String resolvedUser = DemoUserContext.resolve(userId);
        return (int) questionRecords.stream()
                .filter(record -> record.userId().equals(resolvedUser))
                .count();
    }

    public List<KnowledgeRecordDTO> allKnowledge() {
        Optional<List<KnowledgeRecordDTO>> mysqlRecords = readAllKnowledgeFromMysql();
        if (mysqlRecords.isPresent()) {
            return mysqlRecords.get();
        }
        return newestFirst(knowledgeRecords);
    }

    public List<QuestionRecordDTO> allQuestions() {
        Optional<List<QuestionRecordDTO>> mysqlRecords = readAllQuestionsFromMysql();
        if (mysqlRecords.isPresent()) {
            return mysqlRecords.get();
        }
        return newestFirst(questionRecords);
    }

    public int clearKnowledgeForUser(String userId) {
        String resolvedUser = DemoUserContext.resolve(userId);
        int before = knowledgeRecords.size();
        knowledgeRecords.removeIf(record -> record.userId().equals(resolvedUser));
        int cleared = before - knowledgeRecords.size();
        append(new AuditEvent(TYPE_CLEAR_USER_KNOWLEDGE, null, null, resolvedUser, List.of()));
        int mysqlCleared = mirrorKnowledgeClear(resolvedUser);
        if (mysqlReadEnabled()) {
            return Math.max(cleared, mysqlCleared);
        }
        return cleared;
    }

    public int clearKnowledgeForUser(String userId, List<String> ids) {
        String resolvedUser = DemoUserContext.resolve(userId);
        List<String> safeIds = normalizedIds(ids);
        if (safeIds.isEmpty()) {
            return 0;
        }
        int before = knowledgeRecords.size();
        knowledgeRecords.removeIf(record -> record.userId().equals(resolvedUser) && safeIds.contains(record.id()));
        int cleared = before - knowledgeRecords.size();
        append(new AuditEvent(TYPE_CLEAR_USER_KNOWLEDGE_RECORDS, null, null, resolvedUser, safeIds));
        int mysqlCleared = mirrorKnowledgeClear(resolvedUser, safeIds);
        if (mysqlReadEnabled()) {
            return Math.max(cleared, mysqlCleared);
        }
        return cleared;
    }

    public int clearQuestionsForUser(String userId) {
        String resolvedUser = DemoUserContext.resolve(userId);
        int before = questionRecords.size();
        questionRecords.removeIf(record -> record.userId().equals(resolvedUser));
        int cleared = before - questionRecords.size();
        append(new AuditEvent(TYPE_CLEAR_USER_QUESTIONS, null, null, resolvedUser, List.of()));
        int mysqlCleared = mirrorQuestionClear(resolvedUser);
        if (mysqlReadEnabled()) {
            return Math.max(cleared, mysqlCleared);
        }
        return cleared;
    }

    public int clearQuestionsForUser(String userId, List<String> ids) {
        String resolvedUser = DemoUserContext.resolve(userId);
        List<String> safeIds = normalizedIds(ids);
        if (safeIds.isEmpty()) {
            return 0;
        }
        int before = questionRecords.size();
        questionRecords.removeIf(record -> record.userId().equals(resolvedUser) && safeIds.contains(record.id()));
        int cleared = before - questionRecords.size();
        append(new AuditEvent(TYPE_CLEAR_USER_QUESTION_RECORDS, null, null, resolvedUser, safeIds));
        int mysqlCleared = mirrorQuestionClear(resolvedUser, safeIds);
        if (mysqlReadEnabled()) {
            return Math.max(cleared, mysqlCleared);
        }
        return cleared;
    }

    @Override
    public void recordKnowledge(KnowledgeRecordDTO record) {
        appendKnowledge(record);
    }

    @Override
    public void recordQuestion(QuestionRecordDTO record) {
        appendQuestion(record);
    }

    private void appendKnowledge(KnowledgeRecordDTO record) {
        knowledgeRecords.add(record);
        append(new AuditEvent(TYPE_KNOWLEDGE, record, null, null, List.of()));
        mirrorKnowledge(record);
    }

    private void appendQuestion(QuestionRecordDTO record) {
        questionRecords.add(record);
        append(new AuditEvent(TYPE_QUESTION, null, record, null, List.of()));
        mirrorQuestion(record);
    }

    private synchronized void append(AuditEvent event) {
        try {
            Files.createDirectories(auditPath.getParent());
            OpenOption[] options = {StandardOpenOption.CREATE, StandardOpenOption.APPEND};
            Files.writeString(auditPath, objectMapper.writeValueAsString(event) + System.lineSeparator(), StandardCharsets.UTF_8, options);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to append demo audit log: " + auditPath, ex);
        }
    }

    private void loadLine(String line) {
        try {
            AuditEvent event = objectMapper.readValue(line, AuditEvent.class);
            if (TYPE_KNOWLEDGE.equals(event.type()) && event.knowledge() != null) {
                knowledgeRecords.add(event.knowledge());
            }
            if (TYPE_QUESTION.equals(event.type()) && event.question() != null) {
                questionRecords.add(event.question());
            }
            if (TYPE_CLEAR_USER_KNOWLEDGE.equals(event.type()) && event.userId() != null) {
                knowledgeRecords.removeIf(record -> record.userId().equals(event.userId()));
            }
            if (TYPE_CLEAR_USER_QUESTIONS.equals(event.type()) && event.userId() != null) {
                questionRecords.removeIf(record -> record.userId().equals(event.userId()));
            }
            if (TYPE_CLEAR_USER_KNOWLEDGE_RECORDS.equals(event.type()) && event.userId() != null) {
                List<String> ids = normalizedIds(event.recordIds());
                knowledgeRecords.removeIf(record -> record.userId().equals(event.userId()) && ids.contains(record.id()));
            }
            if (TYPE_CLEAR_USER_QUESTION_RECORDS.equals(event.type()) && event.userId() != null) {
                List<String> ids = normalizedIds(event.recordIds());
                questionRecords.removeIf(record -> record.userId().equals(event.userId()) && ids.contains(record.id()));
            }
        } catch (IOException ignored) {
            // Skip malformed demo log lines instead of blocking application startup.
        }
    }

    private static <T> List<T> newestFirst(List<T> records) {
        List<T> result = new ArrayList<>(records);
        result.sort(Comparator.comparing(DemoAuditService::createdAtValue).reversed());
        return result;
    }

    private static String createdAtValue(Object record) {
        if (record instanceof KnowledgeRecordDTO item) {
            return item.createdAt();
        }
        if (record instanceof QuestionRecordDTO item) {
            return item.createdAt();
        }
        return "";
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Optional<List<KnowledgeRecordDTO>> readKnowledgeFromMysql(String resolvedUser) {
        if (!mysqlReadEnabled()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mysqlHistoryMirrorService.orElseThrow().knowledgeForUser(resolvedUser));
        } catch (RuntimeException ex) {
            markMysqlFailure(ex);
            log.warn("MySQL history read failed for knowledge records, falling back to JSONL audit log.", ex);
            return Optional.empty();
        }
    }

    private Optional<List<QuestionRecordDTO>> readQuestionsFromMysql(String resolvedUser) {
        if (!mysqlReadEnabled()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mysqlHistoryMirrorService.orElseThrow().questionsForUser(resolvedUser));
        } catch (RuntimeException ex) {
            markMysqlFailure(ex);
            log.warn("MySQL history read failed for question records, falling back to JSONL audit log.", ex);
            return Optional.empty();
        }
    }

    private Optional<List<KnowledgeRecordDTO>> readAllKnowledgeFromMysql() {
        if (!mysqlReadEnabled()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mysqlHistoryMirrorService.orElseThrow().allKnowledge());
        } catch (RuntimeException ex) {
            markMysqlFailure(ex);
            log.warn("MySQL history read failed for admin knowledge records, falling back to JSONL audit log.", ex);
            return Optional.empty();
        }
    }

    private Optional<List<QuestionRecordDTO>> readAllQuestionsFromMysql() {
        if (!mysqlReadEnabled()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mysqlHistoryMirrorService.orElseThrow().allQuestions());
        } catch (RuntimeException ex) {
            markMysqlFailure(ex);
            log.warn("MySQL history read failed for admin question records, falling back to JSONL audit log.", ex);
            return Optional.empty();
        }
    }

    private void mirrorKnowledge(KnowledgeRecordDTO record) {
        if (!mysqlWriteEnabled()) {
            return;
        }
        try {
            mysqlHistoryMirrorService.orElseThrow().recordKnowledge(record);
        } catch (RuntimeException ex) {
            markMysqlFailure(ex);
            log.warn("MySQL history mirror failed for knowledge record {}.", record.id(), ex);
        }
    }

    private void mirrorQuestion(QuestionRecordDTO record) {
        if (!mysqlWriteEnabled()) {
            return;
        }
        try {
            mysqlHistoryMirrorService.orElseThrow().recordQuestion(record);
        } catch (RuntimeException ex) {
            markMysqlFailure(ex);
            log.warn("MySQL history mirror failed for question record {}.", record.id(), ex);
        }
    }

    private int mirrorKnowledgeClear(String resolvedUser) {
        if (!mysqlWriteEnabled()) {
            return 0;
        }
        try {
            return mysqlHistoryMirrorService.orElseThrow().clearKnowledgeForUser(resolvedUser);
        } catch (RuntimeException ex) {
            markMysqlFailure(ex);
            log.warn("MySQL history mirror failed while clearing knowledge for user {}.", resolvedUser, ex);
            return 0;
        }
    }

    private int mirrorKnowledgeClear(String resolvedUser, List<String> ids) {
        if (!mysqlWriteEnabled()) {
            return 0;
        }
        try {
            return mysqlHistoryMirrorService.orElseThrow().clearKnowledgeForUser(resolvedUser, ids);
        } catch (RuntimeException ex) {
            markMysqlFailure(ex);
            log.warn("MySQL history mirror failed while clearing selected knowledge records for user {}.", resolvedUser, ex);
            return 0;
        }
    }

    private int mirrorQuestionClear(String resolvedUser) {
        if (!mysqlWriteEnabled()) {
            return 0;
        }
        try {
            return mysqlHistoryMirrorService.orElseThrow().clearQuestionsForUser(resolvedUser);
        } catch (RuntimeException ex) {
            markMysqlFailure(ex);
            log.warn("MySQL history mirror failed while clearing questions for user {}.", resolvedUser, ex);
            return 0;
        }
    }

    private int mirrorQuestionClear(String resolvedUser, List<String> ids) {
        if (!mysqlWriteEnabled()) {
            return 0;
        }
        try {
            return mysqlHistoryMirrorService.orElseThrow().clearQuestionsForUser(resolvedUser, ids);
        } catch (RuntimeException ex) {
            markMysqlFailure(ex);
            log.warn("MySQL history mirror failed while clearing selected questions for user {}.", resolvedUser, ex);
            return 0;
        }
    }

    private static List<String> normalizedIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private void markMysqlFailure(RuntimeException ex) {
        mysqlHistoryMirrorService.ifPresent(service -> service.recordFailure(ex));
    }

    private boolean mysqlWriteEnabled() {
        return mysqlProperties.isEnabled() && mysqlHistoryMirrorService.isPresent();
    }

    private boolean mysqlReadEnabled() {
        return mysqlWriteEnabled() && mysqlProperties.isHistoryReadEnabled();
    }

    private static String normalizedRetrievalScope(String value) {
        return RetrievalScope.from(value).wireValue();
    }

    private static String preview(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String compact = value.trim().replaceAll("\\s+", " ");
        if (compact.length() <= PREVIEW_LIMIT) {
            return compact;
        }
        return compact.substring(0, PREVIEW_LIMIT) + "...";
    }

    private record AuditEvent(
            String type,
            KnowledgeRecordDTO knowledge,
            QuestionRecordDTO question,
            String userId,
            List<String> recordIds
    ) {
    }
}
