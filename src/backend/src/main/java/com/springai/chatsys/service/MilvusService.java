package com.springai.chatsys.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.springai.chatsys.config.RagProperties;
import com.springai.chatsys.domain.DocumentChunk;
import com.springai.chatsys.domain.RetrievedChunk;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.request.TruncateCollectionReq;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.utility.request.FlushReq;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class MilvusService {

    private static final String MODE_MILVUS = "milvus";
    private static final String MODE_MEMORY = "memory";
    private static final String FIELD_CHUNK_ID = "chunk_id";
    private static final String FIELD_DOC_ID = "doc_id";
    private static final String FIELD_DOCUMENT_NAME = "document_name";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_CREATED_AT = "created_at";
    private static final String FIELD_EMBEDDING = "embedding";
    private static final List<String> OUTPUT_FIELDS = List.of(
            FIELD_CHUNK_ID,
            FIELD_DOC_ID,
            FIELD_DOCUMENT_NAME,
            FIELD_CONTENT,
            FIELD_SOURCE
    );

    private final RagProperties properties;
    private final List<DocumentChunk> chunks = new CopyOnWriteArrayList<>();

    private volatile MilvusClientV2 client;
    private volatile boolean milvusAvailable;
    private volatile String milvusError;

    public MilvusService(RagProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        if (!isMilvusMode()) {
            return;
        }
        try {
            ensureMilvusInitialized();
        } catch (RuntimeException ex) {
            milvusAvailable = false;
            milvusError = ex.getMessage();
        }
    }

    public void upsertAll(List<DocumentChunk> documentChunks) {
        if (documentChunks == null || documentChunks.isEmpty()) {
            return;
        }
        if (isMilvusMode()) {
            upsertAllToMilvus(documentChunks);
            return;
        }
        upsertAllToMemory(documentChunks);
    }

    public List<RetrievedChunk> search(List<Double> queryEmbedding, int topK) {
        if (isMilvusMode()) {
            return searchMilvus(queryEmbedding, topK);
        }
        int limit = topK > 0 ? topK : properties.getTopK();
        return chunks.stream()
                .map(chunk -> toRetrievedChunk(chunk, cosine(queryEmbedding, chunk.embedding())))
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(limit)
                .toList();
    }

    public int count() {
        if (!isMilvusMode()) {
            return chunks.size();
        }
        try {
            ensureMilvusInitialized();
            GetCollectionStatsResp response = client.getCollectionStats(GetCollectionStatsReq.builder()
                    .databaseName(databaseName())
                    .collectionName(resolvedCollectionName())
                    .build());
            Long count = response.getNumOfEntities();
            if (count == null) {
                return 0;
            }
            return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : count.intValue();
        } catch (RuntimeException ex) {
            markMilvusUnavailable(ex);
            return -1;
        }
    }

    public String mode() {
        return isMilvusMode() ? MODE_MILVUS : MODE_MEMORY;
    }

    public String collectionName() {
        return resolvedCollectionName();
    }

    public boolean available() {
        return !isMilvusMode() || milvusAvailable;
    }

    public Map<String, Object> statusDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("vectorStoreMode", mode());
        details.put("chunkCount", count());
        details.put("available", available());
        if (isMilvusMode()) {
            details.put("collection", resolvedCollectionName());
            details.put("database", databaseName());
            details.put("dimension", milvusProperties().getDimension());
            details.put("endpoint", milvusEndpoint());
            if (milvusError != null && !milvusError.isBlank()) {
                details.put("lastError", milvusError);
            }
        }
        return details;
    }

    public void clear() {
        if (!isMilvusMode()) {
            chunks.clear();
            return;
        }
        MilvusClientV2 activeClient = requireMilvusClient();
        activeClient.truncateCollection(TruncateCollectionReq.builder()
                .databaseName(databaseName())
                .collectionName(resolvedCollectionName())
                .build());
        flush(activeClient);
        loadCollection(activeClient);
    }

    public int deleteByDocIds(List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return 0;
        }
        Set<String> safeDocIds = docIds.stream()
                .filter(docId -> docId != null && !docId.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
        if (safeDocIds.isEmpty()) {
            return 0;
        }
        if (!isMilvusMode()) {
            int before = chunks.size();
            chunks.removeIf(chunk -> safeDocIds.contains(chunk.docId()));
            return before - chunks.size();
        }
        MilvusClientV2 activeClient = requireMilvusClient();
        for (String docId : safeDocIds) {
            activeClient.delete(DeleteReq.builder()
                    .databaseName(databaseName())
                    .collectionName(resolvedCollectionName())
                    .filter(FIELD_DOC_ID + " == " + quoteFilterValue(docId))
                    .build());
        }
        flush(activeClient);
        loadCollection(activeClient);
        return safeDocIds.size();
    }

    public List<DocumentChunk> snapshot() {
        return new ArrayList<>(chunks);
    }

    @PreDestroy
    public void close() {
        MilvusClientV2 activeClient = client;
        if (activeClient != null) {
            activeClient.close();
        }
    }

    private synchronized void ensureMilvusInitialized() {
        if (client != null && milvusAvailable) {
            return;
        }
        initializeMilvus();
        milvusAvailable = true;
        milvusError = null;
    }

    private void initializeMilvus() {
        MilvusClientV2 activeClient = null;
        try {
            activeClient = new MilvusClientV2(connectConfig());
            if (!activeClient.hasCollection(HasCollectionReq.builder()
                    .databaseName(databaseName())
                    .collectionName(resolvedCollectionName())
                    .build())) {
                activeClient.createCollection(createCollectionRequest());
            }
            loadCollection(activeClient);
            client = activeClient;
        } catch (RuntimeException ex) {
            if (activeClient != null) {
                activeClient.close();
            }
            throw ex;
        }
    }

    private void loadCollection(MilvusClientV2 activeClient) {
        activeClient.loadCollection(LoadCollectionReq.builder()
                .databaseName(databaseName())
                .collectionName(resolvedCollectionName())
                .sync(true)
                .build());
    }

    private void upsertAllToMemory(List<DocumentChunk> documentChunks) {
        Set<String> docIds = documentChunks.stream()
                .map(DocumentChunk::docId)
                .collect(Collectors.toSet());
        chunks.removeIf(chunk -> docIds.contains(chunk.docId()));
        chunks.addAll(documentChunks);
    }

    private void upsertAllToMilvus(List<DocumentChunk> documentChunks) {
        MilvusClientV2 activeClient = requireMilvusClient();
        validateDimensions(documentChunks);

        Set<String> docIds = documentChunks.stream()
                .map(DocumentChunk::docId)
                .collect(Collectors.toSet());
        for (String docId : docIds) {
            activeClient.delete(DeleteReq.builder()
                    .databaseName(databaseName())
                    .collectionName(resolvedCollectionName())
                    .filter(FIELD_DOC_ID + " == " + quoteFilterValue(docId))
                    .build());
        }

        activeClient.upsert(UpsertReq.builder()
                .databaseName(databaseName())
                .collectionName(resolvedCollectionName())
                .data(documentChunks.stream().map(this::toJsonObject).toList())
                .build());
        flush(activeClient);
    }

    private List<RetrievedChunk> searchMilvus(List<Double> queryEmbedding, int topK) {
        MilvusClientV2 activeClient = requireMilvusClient();
        int limit = topK > 0 ? topK : properties.getTopK();
        SearchResp response = activeClient.search(SearchReq.builder()
                .databaseName(databaseName())
                .collectionName(resolvedCollectionName())
                .annsField(FIELD_EMBEDDING)
                .metricType(IndexParam.MetricType.COSINE)
                .topK(limit)
                .limit(limit)
                .data(List.of(new FloatVec(toFloatList(queryEmbedding))))
                .outputFields(OUTPUT_FIELDS)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .build());
        if (response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
            return List.of();
        }
        return response.getSearchResults().get(0).stream()
                .map(this::toRetrievedChunk)
                .toList();
    }

    private ConnectConfig connectConfig() {
        RagProperties.Milvus milvus = milvusProperties();
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(milvusEndpoint())
                .dbName(databaseName());
        if (hasText(milvus.getToken())) {
            builder.token(milvus.getToken().trim());
        }
        if (hasText(milvus.getUsername())) {
            builder.username(milvus.getUsername().trim());
        }
        if (hasText(milvus.getPassword())) {
            builder.password(milvus.getPassword().trim());
        }
        return builder.build();
    }

    private void flush(MilvusClientV2 activeClient) {
        activeClient.flush(FlushReq.builder()
                .databaseName(databaseName())
                .collectionNames(List.of(resolvedCollectionName()))
                .waitFlushedTimeoutMs(30_000L)
                .build());
    }

    private CreateCollectionReq createCollectionRequest() {
        CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CHUNK_ID)
                .dataType(DataType.VarChar)
                .isPrimaryKey(true)
                .autoID(false)
                .maxLength(256)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_DOC_ID)
                .dataType(DataType.VarChar)
                .maxLength(256)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_DOCUMENT_NAME)
                .dataType(DataType.VarChar)
                .maxLength(512)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CONTENT)
                .dataType(DataType.VarChar)
                .maxLength(16384)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_SOURCE)
                .dataType(DataType.VarChar)
                .maxLength(1024)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CREATED_AT)
                .dataType(DataType.Int64)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_EMBEDDING)
                .dataType(DataType.FloatVector)
                .dimension(milvusProperties().getDimension())
                .build());

        IndexParam vectorIndex = IndexParam.builder()
                .fieldName(FIELD_EMBEDDING)
                .indexName(FIELD_EMBEDDING + "_idx")
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE)
                .build();

        return CreateCollectionReq.builder()
                .databaseName(databaseName())
                .collectionName(resolvedCollectionName())
                .description("Spring AI Chat Sys RAG document chunks")
                .collectionSchema(schema)
                .indexParams(List.of(vectorIndex))
                .consistencyLevel(ConsistencyLevel.STRONG)
                .build();
    }

    private JsonObject toJsonObject(DocumentChunk chunk) {
        JsonObject row = new JsonObject();
        row.addProperty(FIELD_CHUNK_ID, truncate(chunk.chunkId(), 256));
        row.addProperty(FIELD_DOC_ID, truncate(chunk.docId(), 256));
        row.addProperty(FIELD_DOCUMENT_NAME, truncate(chunk.documentName(), 512));
        row.addProperty(FIELD_CONTENT, truncate(chunk.content(), 16384));
        row.addProperty(FIELD_SOURCE, truncate(chunk.source(), 1024));
        row.addProperty(FIELD_CREATED_AT, chunk.createdAt().toEpochMilli());
        JsonArray embedding = new JsonArray();
        for (Double value : chunk.embedding()) {
            embedding.add(value == null ? 0.0f : value.floatValue());
        }
        row.add(FIELD_EMBEDDING, embedding);
        return row;
    }

    private RetrievedChunk toRetrievedChunk(SearchResp.SearchResult result) {
        Map<String, Object> entity = result.getEntity();
        return new RetrievedChunk(
                stringValue(entity.get(FIELD_CHUNK_ID)),
                stringValue(entity.get(FIELD_DOC_ID)),
                stringValue(entity.get(FIELD_DOCUMENT_NAME)),
                stringValue(entity.get(FIELD_CONTENT)),
                stringValue(entity.get(FIELD_SOURCE)),
                result.getScore() == null ? 0.0d : result.getScore()
        );
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

    private void validateDimensions(List<DocumentChunk> documentChunks) {
        int expected = milvusProperties().getDimension();
        for (DocumentChunk chunk : documentChunks) {
            int actual = chunk.embedding() == null ? 0 : chunk.embedding().size();
            if (actual != expected) {
                throw new IllegalArgumentException("Embedding dimension " + actual
                        + " does not match Milvus dimension " + expected
                        + ". Set RAG_MILVUS_DIMENSION to match the active embedding model.");
            }
        }
    }

    private MilvusClientV2 requireMilvusClient() {
        if (!milvusAvailable || client == null) {
            try {
                ensureMilvusInitialized();
            } catch (RuntimeException ex) {
                markMilvusUnavailable(ex);
                throw new IllegalStateException("Milvus vector store is not available: " + ex.getMessage(), ex);
            }
        }
        if (!milvusAvailable || client == null) {
            throw new IllegalStateException("Milvus vector store is not available"
                    + (milvusError == null ? "" : ": " + milvusError));
        }
        return client;
    }

    private void markMilvusUnavailable(RuntimeException ex) {
        milvusAvailable = false;
        milvusError = ex.getMessage();
    }

    private boolean isMilvusMode() {
        return MODE_MILVUS.equalsIgnoreCase(properties.getVectorStore().getMode());
    }

    private RagProperties.Milvus milvusProperties() {
        return properties.getVectorStore().getMilvus();
    }

    private String resolvedCollectionName() {
        String collectionName = milvusProperties().getCollectionName();
        return hasText(collectionName) ? collectionName.trim() : "spring_ai_chat_chunks";
    }

    private String databaseName() {
        String databaseName = milvusProperties().getDatabaseName();
        return hasText(databaseName) ? databaseName.trim() : "default";
    }

    private String milvusEndpoint() {
        RagProperties.Milvus milvus = milvusProperties();
        if (hasText(milvus.getUri())) {
            return milvus.getUri().trim();
        }
        return "http://" + milvus.getHost() + ":" + milvus.getPort();
    }

    private static String quoteFilterValue(String value) {
        return "\"" + stringValue(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static List<Float> toFloatList(List<Double> embedding) {
        if (embedding == null) {
            return List.of();
        }
        List<Float> result = new ArrayList<>(embedding.size());
        for (Double item : embedding) {
            result.add(item == null ? 0.0f : item.floatValue());
        }
        return result;
    }

    private static double cosine(List<Double> left, List<Double> right) {
        int size = Math.min(left.size(), right.size());
        double dot = 0.0d;
        for (int index = 0; index < size; index++) {
            dot += left.get(index) * right.get(index);
        }
        return dot;
    }

    private static String truncate(String value, int maxLength) {
        String text = stringValue(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
