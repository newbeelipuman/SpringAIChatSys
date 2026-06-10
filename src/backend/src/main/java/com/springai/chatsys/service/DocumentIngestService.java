package com.springai.chatsys.service;

import com.springai.chatsys.domain.DocumentChunk;
import com.springai.chatsys.dto.IngestRequest;
import com.springai.chatsys.dto.IngestResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIngestService {

    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;

    public DocumentIngestService(ChunkService chunkService, EmbeddingService embeddingService, MilvusService milvusService) {
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
        this.milvusService = milvusService;
    }

    public IngestResponse ingest(IngestRequest request) {
        long start = System.currentTimeMillis();
        String docId = valueOrDefault(request.docId(), "doc-" + UUID.randomUUID());
        String documentName = valueOrDefault(request.documentName(), docId);
        String source = valueOrDefault(request.source(), documentName);
        List<String> texts = chunkService.split(request.content());
        List<DocumentChunk> chunks = new ArrayList<>();
        int dimensions = 0;

        for (int index = 0; index < texts.size(); index++) {
            List<Double> embedding = embeddingService.embed(texts.get(index));
            dimensions = embedding.size();
            chunks.add(new DocumentChunk(
                    docId + "-" + (index + 1),
                    docId,
                    documentName,
                    texts.get(index),
                    source,
                    embedding,
                    Instant.now()
            ));
        }

        milvusService.upsertAll(chunks);
        return new IngestResponse(
                docId,
                documentName,
                chunks.size(),
                dimensions,
                milvusService.mode(),
                System.currentTimeMillis() - start
        );
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
