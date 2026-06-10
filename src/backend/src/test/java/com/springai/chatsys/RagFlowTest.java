package com.springai.chatsys;

import com.springai.chatsys.dto.ChatRequest;
import com.springai.chatsys.dto.ChatResponse;
import com.springai.chatsys.dto.IngestRequest;
import com.springai.chatsys.dto.IngestResponse;
import com.springai.chatsys.service.DocumentIngestService;
import com.springai.chatsys.service.RagChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RagFlowTest {

    @Autowired
    private DocumentIngestService documentIngestService;

    @Autowired
    private RagChatService ragChatService;

    @Test
    void shouldIngestAndChatWithCitations() {
        IngestResponse ingestResponse = documentIngestService.ingest(new IngestRequest(
                "rag-test",
                "RAG Test Document",
                "Spring AI can call chat models and embedding models. Milvus stores vector embeddings for semantic retrieval.",
                "unit-test"
        ));

        ChatResponse response = ragChatService.chat(new ChatRequest("What stores vector embeddings?", 2).question(), 2);

        assertThat(ingestResponse.chunkCount()).isGreaterThan(0);
        assertThat(response.retrievedChunks()).isNotEmpty();
        assertThat(response.citations()).isNotEmpty();
        assertThat(response.answer()).contains("本地演示模式");
        assertThat(response.answer()).contains("Milvus stores vector embeddings");
    }
}
