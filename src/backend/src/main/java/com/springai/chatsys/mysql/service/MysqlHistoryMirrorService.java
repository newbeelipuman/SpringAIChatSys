package com.springai.chatsys.mysql.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springai.chatsys.dto.KnowledgeRecordDTO;
import com.springai.chatsys.dto.QuestionRecordDTO;
import com.springai.chatsys.mysql.entity.IngestHistoryEntity;
import com.springai.chatsys.mysql.entity.KnowledgeDocumentEntity;
import com.springai.chatsys.mysql.entity.QuestionHistoryEntity;
import com.springai.chatsys.mysql.repository.IngestHistoryRepository;
import com.springai.chatsys.mysql.repository.KnowledgeDocumentRepository;
import com.springai.chatsys.mysql.repository.QuestionHistoryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@ConditionalOnProperty(prefix = "mysql", name = "enabled", havingValue = "true")
public class MysqlHistoryMirrorService {

    private final MysqlDemoUserService mysqlDemoUserService;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final IngestHistoryRepository ingestHistoryRepository;
    private final QuestionHistoryRepository questionHistoryRepository;
    private final java.util.Optional<MysqlPermissionService> mysqlPermissionService;
    private final ObjectMapper objectMapper;
    private volatile String lastError;

    public MysqlHistoryMirrorService(
            MysqlDemoUserService mysqlDemoUserService,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            IngestHistoryRepository ingestHistoryRepository,
            QuestionHistoryRepository questionHistoryRepository,
            java.util.Optional<MysqlPermissionService> mysqlPermissionService,
            ObjectMapper objectMapper
    ) {
        this.mysqlDemoUserService = mysqlDemoUserService;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.ingestHistoryRepository = ingestHistoryRepository;
        this.questionHistoryRepository = questionHistoryRepository;
        this.mysqlPermissionService = mysqlPermissionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordKnowledge(KnowledgeRecordDTO record) {
        clearFailure();
        mysqlDemoUserService.ensureUser(record.userId());

        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setUserKey(record.userId());
        document.setDocId(record.docId());
        document.setDocumentName(record.documentName());
        document.setSource(record.source());
        document.setStorageScope("persistent");
        document.setVectorStoreMode(record.vectorStoreMode());
        document.setChunkCount(record.chunkCount());
        document.setEmbeddingDimensions(record.embeddingDimensions());
        document.setContentPreview(record.contentPreview());
        knowledgeDocumentRepository.save(document);

        IngestHistoryEntity ingest = new IngestHistoryEntity();
        ingest.setUserKey(record.userId());
        ingest.setDocId(record.docId());
        ingest.setDocumentName(record.documentName());
        ingest.setSource(record.source());
        ingest.setVectorStoreMode(record.vectorStoreMode());
        ingest.setChunkCount(record.chunkCount());
        ingest.setEmbeddingDimensions(record.embeddingDimensions());
        ingest.setContentPreview(record.contentPreview());
        ingestHistoryRepository.save(ingest);

        mysqlPermissionService.ifPresent(service -> service.grantOwnerAndRead(record.userId(), record.docId()));
    }

    @Transactional
    public void recordQuestion(QuestionRecordDTO record) {
        clearFailure();
        mysqlDemoUserService.ensureUser(record.userId());

        QuestionHistoryEntity question = new QuestionHistoryEntity();
        question.setUserKey(record.userId());
        question.setQuestion(record.question());
        question.setAnswerPreview(record.answerPreview());
        question.setMode(record.mode());
        question.setRequestType(record.requestType());
        question.setTopK(record.topK());
        question.setRetrievalScope(record.retrievalScope());
        question.setRetrievedChunkCount(record.retrievedChunkCount());
        question.setCitationChunkIdsJson(toJson(record));
        question.setElapsedMs(record.elapsedMs());
        questionHistoryRepository.save(question);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeRecordDTO> knowledgeForUser(String userKey) {
        clearFailure();
        return knowledgeDocumentRepository.findByUserKeyOrderByCreatedAtDesc(userKey)
                .stream()
                .map(this::toKnowledgeRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeRecordDTO> allKnowledge() {
        clearFailure();
        return knowledgeDocumentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toKnowledgeRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuestionRecordDTO> questionsForUser(String userKey) {
        clearFailure();
        return questionHistoryRepository.findByUserKeyOrderByCreatedAtDesc(userKey)
                .stream()
                .map(this::toQuestionRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuestionRecordDTO> allQuestions() {
        clearFailure();
        return questionHistoryRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toQuestionRecord)
                .toList();
    }

    @Transactional
    public int clearKnowledgeForUser(String userKey) {
        clearFailure();
        long count = knowledgeDocumentRepository.countByUserKey(userKey);
        ingestHistoryRepository.deleteByUserKey(userKey);
        knowledgeDocumentRepository.deleteByUserKey(userKey);
        return toIntCount(count);
    }

    @Transactional
    public int clearKnowledgeForUser(String userKey, List<String> ids) {
        clearFailure();
        List<Long> numericIds = toLongIds(ids);
        if (numericIds.isEmpty()) {
            return 0;
        }
        List<KnowledgeDocumentEntity> matched = knowledgeDocumentRepository.findByUserKeyAndIdIn(userKey, numericIds);
        if (matched.isEmpty()) {
            return 0;
        }
        List<String> docIds = matched.stream()
                .map(KnowledgeDocumentEntity::getDocId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!docIds.isEmpty()) {
            ingestHistoryRepository.deleteByUserKeyAndDocIdIn(userKey, docIds);
        }
        knowledgeDocumentRepository.deleteByUserKeyAndIdIn(userKey, matched.stream().map(KnowledgeDocumentEntity::getId).toList());
        return matched.size();
    }

    @Transactional
    public int clearQuestionsForUser(String userKey) {
        clearFailure();
        long count = questionHistoryRepository.countByUserKey(userKey);
        questionHistoryRepository.deleteByUserKey(userKey);
        return toIntCount(count);
    }

    @Transactional
    public int clearQuestionsForUser(String userKey, List<String> ids) {
        clearFailure();
        List<Long> numericIds = toLongIds(ids);
        if (numericIds.isEmpty()) {
            return 0;
        }
        List<QuestionHistoryEntity> matched = questionHistoryRepository.findByUserKeyAndIdIn(userKey, numericIds);
        if (matched.isEmpty()) {
            return 0;
        }
        questionHistoryRepository.deleteByUserKeyAndIdIn(userKey, matched.stream().map(QuestionHistoryEntity::getId).toList());
        return matched.size();
    }

    public String lastError() {
        return lastError;
    }

    public void recordFailure(Exception ex) {
        lastError = summarize(ex);
    }

    private KnowledgeRecordDTO toKnowledgeRecord(KnowledgeDocumentEntity entity) {
        return new KnowledgeRecordDTO(
                String.valueOf(entity.getId()),
                entity.getUserKey(),
                entity.getDocId(),
                entity.getDocumentName(),
                entity.getSource(),
                entity.getVectorStoreMode(),
                entity.getChunkCount(),
                entity.getEmbeddingDimensions(),
                entity.getContentPreview(),
                entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString()
        );
    }

    private QuestionRecordDTO toQuestionRecord(QuestionHistoryEntity entity) {
        return new QuestionRecordDTO(
                String.valueOf(entity.getId()),
                entity.getUserKey(),
                entity.getQuestion(),
                entity.getAnswerPreview(),
                entity.getMode(),
                entity.getRequestType(),
                entity.getTopK(),
                entity.getRetrievalScope(),
                entity.getRetrievedChunkCount(),
                citationChunkIds(entity.getCitationChunkIdsJson()),
                entity.getElapsedMs(),
                entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString()
        );
    }

    private String toJson(QuestionRecordDTO record) {
        try {
            return objectMapper.writeValueAsString(record.citationChunkIds());
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private List<String> citationChunkIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private void clearFailure() {
        lastError = null;
    }

    private String summarize(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 240 ? message.substring(0, 240) + "..." : message;
    }

    private int toIntCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private List<Long> toLongIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .map(this::toLongId)
                .flatMap(java.util.Optional::stream)
                .distinct()
                .toList();
    }

    private java.util.Optional<Long> toLongId(String id) {
        if (id == null || id.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Long.parseLong(id.trim()));
        } catch (NumberFormatException ex) {
            return java.util.Optional.empty();
        }
    }
}
