package com.springai.chatsys.service;

import com.springai.chatsys.domain.RetrievedChunk;
import com.springai.chatsys.config.RagProperties;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class RetrievalService {

    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final TemporaryKnowledgeService temporaryKnowledgeService;
    private final RagProperties properties;

    public RetrievalService(
            EmbeddingService embeddingService,
            MilvusService milvusService,
            TemporaryKnowledgeService temporaryKnowledgeService,
            RagProperties properties
    ) {
        this.embeddingService = embeddingService;
        this.milvusService = milvusService;
        this.temporaryKnowledgeService = temporaryKnowledgeService;
        this.properties = properties;
    }

    public List<RetrievedChunk> search(String question, Integer topK) {
        return search(DemoUserContext.DEFAULT_USER_ID, question, topK, RetrievalScope.PERSISTENT.wireValue());
    }

    public List<RetrievedChunk> search(String userId, String question, Integer topK, String scopeValue) {
        List<Double> queryEmbedding = embeddingService.embed(question);
        int requestedTopK = topK == null || topK <= 0 ? properties.getTopK() : topK;
        RetrievalScope scope = RetrievalScope.from(scopeValue);

        List<RetrievedChunk> chunks = switch (scope) {
            case TEMPORARY -> temporaryKnowledgeService.search(userId, queryEmbedding, requestedTopK);
            case ALL -> combinedSearch(userId, queryEmbedding, requestedTopK);
            case PERSISTENT -> milvusService.search(queryEmbedding, requestedTopK);
        };

        return chunks
                .stream()
                .filter(chunk -> chunk.score() >= properties.getMinScore())
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(requestedTopK)
                .toList();
    }

    private List<RetrievedChunk> combinedSearch(String userId, List<Double> queryEmbedding, int topK) {
        List<RetrievedChunk> persistent = milvusService.search(queryEmbedding, topK);
        List<RetrievedChunk> temporary = temporaryKnowledgeService.search(userId, queryEmbedding, topK);
        return java.util.stream.Stream.concat(persistent.stream(), temporary.stream())
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(topK)
                .toList();
    }
}
