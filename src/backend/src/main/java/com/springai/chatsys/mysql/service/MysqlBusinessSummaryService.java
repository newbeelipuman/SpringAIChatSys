package com.springai.chatsys.mysql.service;

import com.springai.chatsys.dto.MysqlBusinessSummaryDTO;
import com.springai.chatsys.mysql.repository.IngestHistoryRepository;
import com.springai.chatsys.mysql.repository.KnowledgeDocumentRepository;
import com.springai.chatsys.mysql.repository.QuestionHistoryRepository;
import com.springai.chatsys.mysql.repository.UserSuggestionCacheRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "mysql", name = "enabled", havingValue = "true")
public class MysqlBusinessSummaryService {

    private final MysqlDemoUserService mysqlDemoUserService;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final IngestHistoryRepository ingestHistoryRepository;
    private final QuestionHistoryRepository questionHistoryRepository;
    private final UserSuggestionCacheRepository userSuggestionCacheRepository;
    private volatile String lastError;

    public MysqlBusinessSummaryService(
            MysqlDemoUserService mysqlDemoUserService,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            IngestHistoryRepository ingestHistoryRepository,
            QuestionHistoryRepository questionHistoryRepository,
            UserSuggestionCacheRepository userSuggestionCacheRepository
    ) {
        this.mysqlDemoUserService = mysqlDemoUserService;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.ingestHistoryRepository = ingestHistoryRepository;
        this.questionHistoryRepository = questionHistoryRepository;
        this.userSuggestionCacheRepository = userSuggestionCacheRepository;
    }

    @Transactional
    public MysqlBusinessSummaryDTO summaryForUser(String userKey) {
        try {
            mysqlDemoUserService.ensureUser(userKey);
            MysqlBusinessSummaryDTO summary = new MysqlBusinessSummaryDTO(
                    true,
                    userKey,
                    mysqlDemoUserService.totalUsers(),
                    knowledgeDocumentRepository.count(),
                    ingestHistoryRepository.count(),
                    questionHistoryRepository.count(),
                    knowledgeDocumentRepository.countByUserKey(userKey),
                    ingestHistoryRepository.countByUserKey(userKey),
                    questionHistoryRepository.countByUserKey(userKey),
                    userSuggestionCacheRepository.existsByUserKey(userKey),
                    ""
            );
            lastError = null;
            return summary;
        } catch (RuntimeException ex) {
            recordFailure(ex);
            return new MysqlBusinessSummaryDTO(true, userKey, 0, 0, 0, 0, 0, 0, 0, false, lastError);
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
}
