package com.springai.chatsys.service;

import com.springai.chatsys.domain.DocumentChunk;
import com.springai.chatsys.domain.RetrievedChunk;
import com.springai.chatsys.dto.IngestRequest;
import com.springai.chatsys.dto.IngestResponse;
import com.springai.chatsys.dto.KnowledgeRecordDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TemporaryKnowledgeService {

    private static final int PREVIEW_LIMIT = 240;
    private static final String VECTOR_STORE_MODE = "temporary-memory";

    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final Map<String, CopyOnWriteArrayList<DocumentChunk>> chunksByUser = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<KnowledgeRecordDTO>> recordsByUser = new ConcurrentHashMap<>();

    public TemporaryKnowledgeService(ChunkService chunkService, EmbeddingService embeddingService) {
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
    }

    public IngestResponse ingest(String userId, IngestRequest request) {
        long start = System.currentTimeMillis();
        String resolvedUser = DemoUserContext.resolve(userId);
        String docId = valueOrDefault(request.docId(), "tmp-" + UUID.randomUUID());
        String documentName = valueOrDefault(request.documentName(), docId);
        String source = valueOrDefault(request.source(), "临时录入");
        List<String> texts = chunkService.split(request.content());
        List<DocumentChunk> chunks = new ArrayList<>();
        int dimensions = 0;

        for (int index = 0; index < texts.size(); index++) {
            List<Double> embedding = embeddingService.embed(texts.get(index));
            dimensions = embedding.size();
            chunks.add(new DocumentChunk(
                    "tmp-" + resolvedUser + "-" + docId + "-" + (index + 1),
                    docId,
                    documentName,
                    texts.get(index),
                    source,
                    embedding,
                    Instant.now()
            ));
        }

        CopyOnWriteArrayList<DocumentChunk> userChunks = chunksByUser.computeIfAbsent(resolvedUser, ignored -> new CopyOnWriteArrayList<>());
        userChunks.removeIf(chunk -> chunk.docId().equals(docId));
        userChunks.addAll(chunks);

        CopyOnWriteArrayList<KnowledgeRecordDTO> userRecords = recordsByUser.computeIfAbsent(resolvedUser, ignored -> new CopyOnWriteArrayList<>());
        userRecords.removeIf(record -> record.docId().equals(docId));
        userRecords.add(new KnowledgeRecordDTO(
                UUID.randomUUID().toString(),
                resolvedUser,
                docId,
                documentName,
                source,
                VECTOR_STORE_MODE,
                chunks.size(),
                dimensions,
                preview(request.content()),
                Instant.now().toString()
        ));

        return new IngestResponse(
                docId,
                documentName,
                chunks.size(),
                dimensions,
                VECTOR_STORE_MODE,
                System.currentTimeMillis() - start
        );
    }

    public List<RetrievedChunk> search(String userId, List<Double> queryEmbedding, int topK) {
        String resolvedUser = DemoUserContext.resolve(userId);
        int limit = topK > 0 ? topK : Integer.MAX_VALUE;
        return chunksByUser.getOrDefault(resolvedUser, new CopyOnWriteArrayList<>())
                .stream()
                .map(chunk -> toRetrievedChunk(chunk, cosine(queryEmbedding, chunk.embedding())))
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(limit)
                .toList();
    }

    public List<KnowledgeRecordDTO> recordsForUser(String userId) {
        String resolvedUser = DemoUserContext.resolve(userId);
        List<KnowledgeRecordDTO> result = new ArrayList<>(recordsByUser.getOrDefault(resolvedUser, new CopyOnWriteArrayList<>()));
        result.sort(Comparator.comparing(KnowledgeRecordDTO::createdAt).reversed());
        return result;
    }

    public int countForUser(String userId) {
        String resolvedUser = DemoUserContext.resolve(userId);
        return chunksByUser.getOrDefault(resolvedUser, new CopyOnWriteArrayList<>()).size();
    }

    public void clearForUser(String userId) {
        String resolvedUser = DemoUserContext.resolve(userId);
        chunksByUser.remove(resolvedUser);
        recordsByUser.remove(resolvedUser);
    }

    public int clearRecordsForUser(String userId, List<String> recordIds) {
        String resolvedUser = DemoUserContext.resolve(userId);
        List<String> safeIds = normalizedIds(recordIds);
        if (safeIds.isEmpty()) {
            return 0;
        }
        CopyOnWriteArrayList<KnowledgeRecordDTO> records = recordsByUser.get(resolvedUser);
        if (records == null || records.isEmpty()) {
            return 0;
        }
        List<String> docIds = records.stream()
                .filter(record -> safeIds.contains(record.id()))
                .map(KnowledgeRecordDTO::docId)
                .distinct()
                .toList();
        if (docIds.isEmpty()) {
            return 0;
        }
        records.removeIf(record -> safeIds.contains(record.id()));
        CopyOnWriteArrayList<DocumentChunk> chunks = chunksByUser.get(resolvedUser);
        if (chunks == null) {
            return 0;
        }
        int before = chunks.size();
        chunks.removeIf(chunk -> docIds.contains(chunk.docId()));
        return before - chunks.size();
    }

    private static RetrievedChunk toRetrievedChunk(DocumentChunk chunk, double score) {
        return new RetrievedChunk(
                chunk.chunkId(),
                chunk.docId(),
                chunk.documentName(),
                chunk.content(),
                chunk.source(),
                score
        );
    }

    private static double cosine(List<Double> left, List<Double> right) {
        int size = Math.min(left.size(), right.size());
        double dot = 0.0d;
        for (int index = 0; index < size; index++) {
            dot += left.get(index) * right.get(index);
        }
        return dot;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
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
}
