package com.springai.chatsys.mysql.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springai.chatsys.mysql.entity.KnowledgeDocumentEntity;
import com.springai.chatsys.mysql.entity.UserSuggestionCacheEntity;
import com.springai.chatsys.mysql.repository.KnowledgeDocumentRepository;
import com.springai.chatsys.mysql.repository.UserSuggestionCacheRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "mysql", name = "enabled", havingValue = "true")
public class MysqlSuggestionCacheService {

    private static final int MAX_SUGGESTIONS = 6;

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final UserSuggestionCacheRepository userSuggestionCacheRepository;
    private final ObjectMapper objectMapper;
    private volatile String lastError;

    public MysqlSuggestionCacheService(
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            UserSuggestionCacheRepository userSuggestionCacheRepository,
            ObjectMapper objectMapper
    ) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.userSuggestionCacheRepository = userSuggestionCacheRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<String> suggestionsForUser(String userKey) {
        try {
            List<KnowledgeDocumentEntity> documents = knowledgeDocumentRepository.findByUserKeyOrderByCreatedAtDesc(userKey);
            if (documents.isEmpty()) {
                return List.of();
            }

            String version = knowledgeVersion(documents);
            UserSuggestionCacheEntity cache = userSuggestionCacheRepository.findByUserKey(userKey).orElse(null);
            if (cache != null && version.equals(cache.getKnowledgeVersion())) {
                List<String> cached = parse(cache.getSuggestionsJson());
                if (!cached.isEmpty()) {
                    lastError = null;
                    return cached;
                }
            }

            List<String> suggestions = buildSuggestions(documents);
            saveCache(userKey, version, suggestions, cache);
            lastError = null;
            return suggestions;
        } catch (RuntimeException ex) {
            recordFailure(ex);
            throw ex;
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

    private void saveCache(String userKey, String version, List<String> suggestions, UserSuggestionCacheEntity cache) {
        UserSuggestionCacheEntity target = cache == null ? new UserSuggestionCacheEntity() : cache;
        target.setUserKey(userKey);
        target.setKnowledgeVersion(version);
        target.setSuggestionsJson(toJson(suggestions));
        userSuggestionCacheRepository.save(target);
    }

    private List<String> buildSuggestions(List<KnowledgeDocumentEntity> documents) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (KnowledgeDocumentEntity document : documents) {
            String name = firstText(document.getDocumentName(), document.getDocId(), document.getSource());
            if (!StringUtils.hasText(name)) {
                continue;
            }
            result.add("请概括《" + name + "》的核心内容");
            result.add("《" + name + "》里有哪些关键结论？");
            if (StringUtils.hasText(document.getSource()) && !document.getSource().equals(name)) {
                result.add("根据来源 " + document.getSource() + "，我应该关注哪些信息？");
            }
            if (result.size() >= MAX_SUGGESTIONS) {
                break;
            }
        }
        return new ArrayList<>(result).stream().limit(MAX_SUGGESTIONS).toList();
    }

    private String knowledgeVersion(List<KnowledgeDocumentEntity> documents) {
        KnowledgeDocumentEntity latest = documents.get(0);
        String latestTime = latest.getCreatedAt() == null ? "" : latest.getCreatedAt().toString();
        return documents.size() + ":" + latestTime;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private List<String> parse(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String toJson(List<String> suggestions) {
        try {
            return objectMapper.writeValueAsString(suggestions);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
}
