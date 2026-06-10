package com.springai.chatsys;

import com.springai.chatsys.dto.IngestRequest;
import com.springai.chatsys.service.DocumentIngestService;
import com.springai.chatsys.service.MilvusService;
import com.springai.chatsys.service.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "ENABLE_MILVUS_SMOKE_TEST", matches = "true")
@SpringBootTest(properties = {
        "rag.vector-store.mode=milvus",
        "rag.vector-store.milvus.collection-name=spring_ai_chat_chunks_smoke",
        "rag.vector-store.milvus.dimension=384"
})
class MilvusSmokeTest {

    @Autowired
    private DocumentIngestService documentIngestService;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private MilvusService milvusService;

    @BeforeEach
    void resetMilvusCollection() {
        milvusService.clear();
    }

    @Test
    void shouldUpsertAndSearchMilvus() {
        documentIngestService.ingest(new IngestRequest(
                "milvus-smoke",
                "Milvus Smoke Document",
                "Milvus persists vector embeddings and supports cosine similarity search for RAG retrieval.",
                "milvus-smoke-test"
        ));

        assertThat(milvusService.mode()).isEqualTo("milvus");
        assertThat(milvusService.available()).isTrue();
        assertThat(milvusService.count()).isGreaterThan(0);
        assertThat(retrievalService.search("What persists vector embeddings?", 3))
                .anySatisfy(chunk -> {
                    assertThat(chunk.docId()).isEqualTo("milvus-smoke");
                    assertThat(chunk.chunkId()).isEqualTo("milvus-smoke-1");
                });
    }
}
