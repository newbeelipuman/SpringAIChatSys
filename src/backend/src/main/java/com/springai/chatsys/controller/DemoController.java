package com.springai.chatsys.controller;

import com.springai.chatsys.auth.CurrentUser;
import com.springai.chatsys.auth.AuthAccessService;
import com.springai.chatsys.auth.AuthPermission;
import com.springai.chatsys.auth.IdentityResolver;
import com.springai.chatsys.dto.AdminUserDataDeleteResponse;
import com.springai.chatsys.config.RagProperties;
import com.springai.chatsys.domain.RetrievedChunk;
import com.springai.chatsys.dto.ChatRequest;
import com.springai.chatsys.dto.ChatResponse;
import com.springai.chatsys.dto.DemoMaterialDTO;
import com.springai.chatsys.dto.HealthResponse;
import com.springai.chatsys.dto.HistoryClearResponse;
import com.springai.chatsys.dto.HistoryMergeRequest;
import com.springai.chatsys.dto.IngestRequest;
import com.springai.chatsys.dto.IngestMaterialsResponse;
import com.springai.chatsys.dto.IngestResponse;
import com.springai.chatsys.dto.KnowledgeRecordDTO;
import com.springai.chatsys.dto.MysqlBusinessSummaryDTO;
import com.springai.chatsys.dto.MysqlDiagnosticsDTO;
import com.springai.chatsys.dto.PageResponse;
import com.springai.chatsys.dto.QuestionRecordDTO;
import com.springai.chatsys.dto.RecordIdBatchRequest;
import com.springai.chatsys.dto.RetrievedChunkDTO;
import com.springai.chatsys.dto.SearchRequest;
import com.springai.chatsys.dto.SearchResponse;
import com.springai.chatsys.service.DemoAuditService;
import com.springai.chatsys.service.DemoMaterialsService;
import com.springai.chatsys.service.DemoUserContext;
import com.springai.chatsys.service.DocumentIngestService;
import com.springai.chatsys.service.EmbeddingService;
import com.springai.chatsys.service.MilvusService;
import com.springai.chatsys.mysql.service.MysqlBusinessSummaryService;
import com.springai.chatsys.mysql.service.MysqlAuthService;
import com.springai.chatsys.mysql.service.MysqlDemoUserService;
import com.springai.chatsys.mysql.service.MysqlDiagnosticsService;
import com.springai.chatsys.mysql.service.MysqlSuggestionCacheService;
import com.springai.chatsys.mysql.service.MysqlStatusService;
import com.springai.chatsys.service.RagChatService;
import com.springai.chatsys.service.RetrievalService;
import com.springai.chatsys.service.TemporaryKnowledgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Optional;

@RestController
@RequestMapping("/demo")
public class DemoController {

    private final DocumentIngestService documentIngestService;
    private final DemoMaterialsService demoMaterialsService;
    private final RetrievalService retrievalService;
    private final RagChatService ragChatService;
    private final MilvusService milvusService;
    private final EmbeddingService embeddingService;
    private final RagProperties properties;
    private final DemoAuditService demoAuditService;
    private final TemporaryKnowledgeService temporaryKnowledgeService;
    private final IdentityResolver identityResolver;
    private final AuthAccessService authAccessService;
    private final MysqlStatusService mysqlStatusService;
    private final Optional<MysqlAuthService> mysqlAuthService;
    private final Optional<MysqlDemoUserService> mysqlDemoUserService;
    private final Optional<MysqlBusinessSummaryService> mysqlBusinessSummaryService;
    private final Optional<MysqlDiagnosticsService> mysqlDiagnosticsService;
    private final Optional<MysqlSuggestionCacheService> mysqlSuggestionCacheService;

    public DemoController(
            DocumentIngestService documentIngestService,
            DemoMaterialsService demoMaterialsService,
            RetrievalService retrievalService,
            RagChatService ragChatService,
            MilvusService milvusService,
            EmbeddingService embeddingService,
            RagProperties properties,
            DemoAuditService demoAuditService,
            TemporaryKnowledgeService temporaryKnowledgeService,
            IdentityResolver identityResolver,
            AuthAccessService authAccessService,
            MysqlStatusService mysqlStatusService,
            Optional<MysqlAuthService> mysqlAuthService,
            Optional<MysqlDemoUserService> mysqlDemoUserService,
            Optional<MysqlBusinessSummaryService> mysqlBusinessSummaryService,
            Optional<MysqlDiagnosticsService> mysqlDiagnosticsService,
            Optional<MysqlSuggestionCacheService> mysqlSuggestionCacheService
    ) {
        this.documentIngestService = documentIngestService;
        this.demoMaterialsService = demoMaterialsService;
        this.retrievalService = retrievalService;
        this.ragChatService = ragChatService;
        this.milvusService = milvusService;
        this.embeddingService = embeddingService;
        this.properties = properties;
        this.demoAuditService = demoAuditService;
        this.temporaryKnowledgeService = temporaryKnowledgeService;
        this.identityResolver = identityResolver;
        this.authAccessService = authAccessService;
        this.mysqlStatusService = mysqlStatusService;
        this.mysqlAuthService = mysqlAuthService;
        this.mysqlDemoUserService = mysqlDemoUserService;
        this.mysqlBusinessSummaryService = mysqlBusinessSummaryService;
        this.mysqlDiagnosticsService = mysqlDiagnosticsService;
        this.mysqlSuggestionCacheService = mysqlSuggestionCacheService;
    }

    @GetMapping("/health")
    public HealthResponse health(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        CurrentUser currentUser = currentUser(authorization, userId);
        Map<String, Object> details = milvusService.statusDetails();
        details.put("embeddingMode", embeddingService.mode());
        details.put("defaultTopK", properties.getTopK());
        details.put("minScore", properties.getMinScore());
        details.put("currentIdentitySource", currentUser.source());
        details.put("currentUserId", currentUser.userKey());
        details.put("currentRole", currentUser.role());
        details.put("adminUserAvailable", mysqlAuthService.map(service -> service.adminUserCount() > 0).orElse(false));
        details.put("adminEndpointsProtected", true);
        details.putAll(mysqlStatusService.statusDetails());
        return new HealthResponse("UP", details);
    }

    @PostMapping("/ingest")
    public IngestResponse ingest(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @Valid @RequestBody IngestRequest request
    ) {
        CurrentUser currentUser = currentUser(authorization, userId);
        IngestResponse response = documentIngestService.ingest(request);
        demoAuditService.recordIngest(currentUser.userKey(), request, response);
        return response;
    }

    @PostMapping("/ingest/temporary")
    public IngestResponse ingestTemporary(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @Valid @RequestBody IngestRequest request
    ) {
        return temporaryKnowledgeService.ingest(currentUser(authorization, userId).userKey(), request);
    }

    @GetMapping("/materials")
    public List<DemoMaterialDTO> materials() {
        return demoMaterialsService.listMaterials();
    }

    @PostMapping("/ingest/materials")
    public IngestMaterialsResponse ingestMaterials(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        CurrentUser currentUser = currentUser(authorization, userId);
        IngestMaterialsResponse response = demoMaterialsService.ingestAll();
        demoAuditService.recordMaterialsIngest(currentUser.userKey(), response);
        return response;
    }

    @PostMapping("/reset")
    public HealthResponse reset(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        milvusService.clear();
        return health(authorization, userId);
    }

    @PostMapping("/search")
    public SearchResponse search(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @Valid @RequestBody SearchRequest request
    ) {
        CurrentUser currentUser = currentUser(authorization, userId);
        long start = System.currentTimeMillis();
        List<RetrievedChunk> chunks = retrievalService.search(currentUser.userKey(), request.question(), request.topK(), request.scope());
        SearchResponse response = new SearchResponse(
                request.question(),
                request.topK() == null || request.topK() <= 0 ? properties.getTopK() : request.topK(),
                chunks.stream().map(this::toDto).toList(),
                System.currentTimeMillis() - start
        );
        demoAuditService.recordSearch(currentUser.userKey(), response, request.scope());
        return response;
    }

    @PostMapping("/chat")
    public ChatResponse chat(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @Valid @RequestBody ChatRequest request
    ) {
        CurrentUser currentUser = currentUser(authorization, userId);
        ChatResponse response = ragChatService.chat(currentUser.userKey(), request.question(), request.topK(), request.scope());
        demoAuditService.recordChat(currentUser.userKey(), response, request.scope());
        return response;
    }

    @GetMapping("/me/knowledge")
    public List<KnowledgeRecordDTO> myKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        String resolvedUser = currentUser(authorization, userId).userKey();
        ensureMysqlUser(resolvedUser);
        return demoAuditService.knowledgeForUser(resolvedUser);
    }

    @GetMapping("/me/temporary-knowledge")
    public List<KnowledgeRecordDTO> myTemporaryKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        return temporaryKnowledgeService.recordsForUser(currentUser(authorization, userId).userKey());
    }

    @DeleteMapping("/me/temporary-knowledge")
    public Map<String, Object> clearMyTemporaryKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        String resolvedUser = currentUser(authorization, userId).userKey();
        int clearedChunks = temporaryKnowledgeService.countForUser(resolvedUser);
        temporaryKnowledgeService.clearForUser(resolvedUser);
        return Map.of("clearedChunks", clearedChunks);
    }

    @GetMapping("/me/questions")
    public List<QuestionRecordDTO> myQuestions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        String resolvedUser = currentUser(authorization, userId).userKey();
        ensureMysqlUser(resolvedUser);
        return demoAuditService.questionsForUser(resolvedUser);
    }

    @GetMapping("/me/suggestions")
    public List<String> mySuggestions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        String resolvedUser = currentUser(authorization, userId).userKey();
        ensureMysqlUser(resolvedUser);
        if (mysqlSuggestionCacheService.isEmpty()) {
            return List.of();
        }
        try {
            return mysqlSuggestionCacheService.get().suggestionsForUser(resolvedUser);
        } catch (RuntimeException ex) {
            mysqlSuggestionCacheService.get().recordFailure(ex);
            return List.of();
        }
    }

    @GetMapping("/me/mysql-summary")
    public MysqlBusinessSummaryDTO myMysqlSummary(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        String resolvedUser = currentUser(authorization, userId).userKey();
        return mysqlBusinessSummaryService
                .map(service -> service.summaryForUser(resolvedUser))
                .orElseGet(() -> MysqlBusinessSummaryDTO.disabled(resolvedUser));
    }

    @GetMapping("/me/mysql-diagnostics")
    public MysqlDiagnosticsDTO myMysqlDiagnostics(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        String resolvedUser = currentUser(authorization, userId).userKey();
        int jsonlKnowledge = demoAuditService.jsonlKnowledgeCountForUser(resolvedUser);
        int jsonlQuestions = demoAuditService.jsonlQuestionCountForUser(resolvedUser);
        return mysqlDiagnosticsService
                .map(service -> service.diagnosticsForUser(resolvedUser, jsonlKnowledge, jsonlQuestions))
                .orElseGet(() -> MysqlDiagnosticsDTO.disabled(resolvedUser, jsonlKnowledge, jsonlQuestions));
    }

    @DeleteMapping("/me/questions")
    public HistoryClearResponse clearMyQuestions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        String resolvedUser = currentUser(authorization, userId).userKey();
        ensureMysqlUser(resolvedUser);
        return new HistoryClearResponse(demoAuditService.clearQuestionsForUser(resolvedUser));
    }

    @DeleteMapping("/me/knowledge-records")
    public HistoryClearResponse clearMyKnowledgeRecords(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        String resolvedUser = currentUser(authorization, userId).userKey();
        ensureMysqlUser(resolvedUser);
        return new HistoryClearResponse(demoAuditService.clearKnowledgeForUser(resolvedUser));
    }

    @PostMapping("/me/knowledge-records/delete-selected")
    public HistoryClearResponse clearSelectedMyKnowledgeRecords(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @Valid @RequestBody RecordIdBatchRequest request
    ) {
        String resolvedUser = currentUser(authorization, userId).userKey();
        ensureMysqlUser(resolvedUser);
        List<String> docIds = demoAuditService.knowledgeForUser(resolvedUser).stream()
                .filter(record -> request.ids().contains(record.id()))
                .map(KnowledgeRecordDTO::docId)
                .toList();
        milvusService.deleteByDocIds(docIds);
        return new HistoryClearResponse(demoAuditService.clearKnowledgeForUser(resolvedUser, request.ids()));
    }

    @PostMapping("/me/questions/merge-temporary")
    public IngestResponse mergeQuestionsToTemporaryKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @Valid @RequestBody HistoryMergeRequest request
    ) {
        String resolvedUser = currentUser(authorization, userId).userKey();
        List<QuestionRecordDTO> selected = demoAuditService.questionsForUser(resolvedUser, request.questionIds()).stream()
                .sorted(Comparator.comparing(QuestionRecordDTO::createdAt))
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("没有找到可合并的提问历史。");
        }
        IngestRequest ingestRequest = new IngestRequest(
                valueOrDefault(request.docId(), "merged-history-" + System.currentTimeMillis()),
                valueOrDefault(request.documentName(), "合并的提问历史"),
                toMergedHistoryContent(selected),
                valueOrDefault(request.source(), "提问历史合并")
        );
        return temporaryKnowledgeService.ingest(resolvedUser, ingestRequest);
    }

    @GetMapping("/admin/knowledge")
    public List<KnowledgeRecordDTO> adminKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.VIEW_ADMIN_HISTORY);
        return demoAuditService.allKnowledge();
    }

    @GetMapping("/admin/questions")
    public List<QuestionRecordDTO> adminQuestions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId
    ) {
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.VIEW_ADMIN_HISTORY);
        return demoAuditService.allQuestions();
    }

    @GetMapping("/admin/users/{targetUserKey}/knowledge")
    public List<KnowledgeRecordDTO> adminUserKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @PathVariable String targetUserKey
    ) {
        String resolvedTarget = DemoUserContext.resolve(targetUserKey);
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.VIEW_ADMIN_HISTORY);
        return demoAuditService.knowledgeForUser(resolvedTarget);
    }

    @GetMapping("/admin/users/{targetUserKey}/knowledge/page")
    public PageResponse<KnowledgeRecordDTO> adminUserKnowledgePage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @PathVariable String targetUserKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        String resolvedTarget = DemoUserContext.resolve(targetUserKey);
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.VIEW_ADMIN_HISTORY);
        return PageResponse.of(demoAuditService.knowledgeForUser(resolvedTarget), page, size);
    }

    @GetMapping("/admin/users/{targetUserKey}/questions")
    public List<QuestionRecordDTO> adminUserQuestions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @PathVariable String targetUserKey
    ) {
        String resolvedTarget = DemoUserContext.resolve(targetUserKey);
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.VIEW_ADMIN_HISTORY);
        return demoAuditService.questionsForUser(resolvedTarget);
    }

    @GetMapping("/admin/users/{targetUserKey}/questions/page")
    public PageResponse<QuestionRecordDTO> adminUserQuestionsPage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @PathVariable String targetUserKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        String resolvedTarget = DemoUserContext.resolve(targetUserKey);
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.VIEW_ADMIN_HISTORY);
        return PageResponse.of(demoAuditService.questionsForUser(resolvedTarget), page, size);
    }

    @GetMapping("/admin/users/{targetUserKey}/temporary-knowledge")
    public List<KnowledgeRecordDTO> adminUserTemporaryKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @PathVariable String targetUserKey
    ) {
        String resolvedTarget = DemoUserContext.resolve(targetUserKey);
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.VIEW_ADMIN_HISTORY);
        return temporaryKnowledgeService.recordsForUser(resolvedTarget);
    }

    @GetMapping("/admin/users/{targetUserKey}/temporary-knowledge/page")
    public PageResponse<KnowledgeRecordDTO> adminUserTemporaryKnowledgePage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @PathVariable String targetUserKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        String resolvedTarget = DemoUserContext.resolve(targetUserKey);
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.VIEW_ADMIN_HISTORY);
        return PageResponse.of(temporaryKnowledgeService.recordsForUser(resolvedTarget), page, size);
    }

    @DeleteMapping("/admin/users/{targetUserKey}/knowledge-records")
    public AdminUserDataDeleteResponse clearUserKnowledgeRecords(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @PathVariable String targetUserKey
    ) {
        String resolvedTarget = DemoUserContext.resolve(targetUserKey);
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.DELETE_USER_DATA);
        int clearedKnowledge = demoAuditService.clearKnowledgeForUser(resolvedTarget);
        return new AdminUserDataDeleteResponse(resolvedTarget, clearedKnowledge, 0, 0);
    }

    @PostMapping("/admin/users/{targetUserKey}/knowledge-records/delete-selected")
    public AdminUserDataDeleteResponse clearSelectedUserKnowledgeRecords(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @PathVariable String targetUserKey,
            @Valid @RequestBody RecordIdBatchRequest request
    ) {
        String resolvedTarget = DemoUserContext.resolve(targetUserKey);
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.DELETE_USER_DATA);
        int clearedKnowledge = demoAuditService.clearKnowledgeForUser(resolvedTarget, request.ids());
        return new AdminUserDataDeleteResponse(resolvedTarget, clearedKnowledge, 0, 0);
    }

    @DeleteMapping("/admin/users/{targetUserKey}/questions")
    public AdminUserDataDeleteResponse clearUserQuestions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @PathVariable String targetUserKey
    ) {
        String resolvedTarget = DemoUserContext.resolve(targetUserKey);
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.DELETE_USER_DATA);
        int clearedQuestions = demoAuditService.clearQuestionsForUser(resolvedTarget);
        return new AdminUserDataDeleteResponse(resolvedTarget, 0, clearedQuestions, 0);
    }

    @PostMapping("/admin/users/{targetUserKey}/questions/delete-selected")
    public AdminUserDataDeleteResponse clearSelectedUserQuestions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @PathVariable String targetUserKey,
            @Valid @RequestBody RecordIdBatchRequest request
    ) {
        String resolvedTarget = DemoUserContext.resolve(targetUserKey);
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.DELETE_USER_DATA);
        int clearedQuestions = demoAuditService.clearQuestionsForUser(resolvedTarget, request.ids());
        return new AdminUserDataDeleteResponse(resolvedTarget, 0, clearedQuestions, 0);
    }

    @DeleteMapping("/admin/users/{targetUserKey}/temporary-knowledge")
    public AdminUserDataDeleteResponse clearUserTemporaryKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @PathVariable String targetUserKey
    ) {
        String resolvedTarget = DemoUserContext.resolve(targetUserKey);
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.DELETE_USER_DATA);
        int clearedChunks = temporaryKnowledgeService.countForUser(resolvedTarget);
        temporaryKnowledgeService.clearForUser(resolvedTarget);
        return new AdminUserDataDeleteResponse(resolvedTarget, 0, 0, clearedChunks);
    }

    @PostMapping("/admin/users/{targetUserKey}/temporary-knowledge/delete-selected")
    public AdminUserDataDeleteResponse clearSelectedUserTemporaryKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @PathVariable String targetUserKey,
            @Valid @RequestBody RecordIdBatchRequest request
    ) {
        String resolvedTarget = DemoUserContext.resolve(targetUserKey);
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.DELETE_USER_DATA);
        int clearedChunks = temporaryKnowledgeService.clearRecordsForUser(resolvedTarget, request.ids());
        return new AdminUserDataDeleteResponse(resolvedTarget, 0, 0, clearedChunks);
    }

    @DeleteMapping("/admin/users/{targetUserKey}/data")
    public AdminUserDataDeleteResponse clearUserData(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @PathVariable String targetUserKey
    ) {
        String resolvedTarget = DemoUserContext.resolve(targetUserKey);
        authAccessService.requirePermission(currentUser(authorization, userId), AuthPermission.DELETE_USER_DATA);
        int clearedKnowledge = demoAuditService.clearKnowledgeForUser(resolvedTarget);
        int clearedQuestions = demoAuditService.clearQuestionsForUser(resolvedTarget);
        int clearedChunks = temporaryKnowledgeService.countForUser(resolvedTarget);
        temporaryKnowledgeService.clearForUser(resolvedTarget);
        return new AdminUserDataDeleteResponse(resolvedTarget, clearedKnowledge, clearedQuestions, clearedChunks);
    }

    private RetrievedChunkDTO toDto(RetrievedChunk chunk) {
        return new RetrievedChunkDTO(chunk.chunkId(), chunk.docId(), chunk.documentName(), chunk.content(), chunk.source(), chunk.score());
    }

    private static String toMergedHistoryContent(List<QuestionRecordDTO> records) {
        StringBuilder builder = new StringBuilder("# 合并的提问历史\n");
        for (QuestionRecordDTO record : records) {
            builder.append("\n## ").append(record.question()).append('\n');
            builder.append("- 类型：").append(record.requestType()).append('\n');
            builder.append("- 模式：").append(record.mode()).append('\n');
            builder.append("- 命中片段：").append(record.retrievedChunkCount()).append('\n');
            if (!record.answerPreview().isBlank()) {
                builder.append("- 回答摘要：").append(record.answerPreview()).append('\n');
            }
            if (!record.citationChunkIds().isEmpty()) {
                builder.append("- 引用片段：").append(String.join(", ", record.citationChunkIds())).append('\n');
            }
        }
        return builder.toString();
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void ensureMysqlUser(String userId) {
        if (mysqlDemoUserService.isEmpty()) {
            return;
        }
        try {
            mysqlDemoUserService.get().ensureUser(DemoUserContext.resolve(userId));
        } catch (RuntimeException ignored) {
            // MySQL user upsert must not block the demo flow.
        }
    }

    private CurrentUser currentUser(String authorization, String userId) {
        return identityResolver.resolve(authorization, userId);
    }
}
