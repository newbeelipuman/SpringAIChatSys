package com.springai.chatsys;

import com.springai.chatsys.dto.IngestRequest;
import com.springai.chatsys.dto.IngestResponse;
import com.springai.chatsys.service.MilvusService;
import com.springai.chatsys.service.RagChatService;
import com.springai.chatsys.service.RetrievalService;
import com.springai.chatsys.service.TemporaryKnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TemporaryKnowledgeServiceTest {

    @Autowired
    private TemporaryKnowledgeService temporaryKnowledgeService;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private MilvusService milvusService;

    @Autowired
    private RagChatService ragChatService;

    @Test
    void shouldKeepTemporaryKnowledgeUserScopedAndOutOfPersistentStore() {
        String owner = "temporary-owner";
        String otherUser = "temporary-other";
        temporaryKnowledgeService.clearForUser(owner);
        temporaryKnowledgeService.clearForUser(otherUser);
        int persistentCountBefore = milvusService.count();

        IngestResponse response = temporaryKnowledgeService.ingest(owner, new IngestRequest(
                "ephemeral-doc",
                "Ephemeral Demo Document",
                "zephyr-only temporary note says approval code alpha-bravo-charlie belongs to the current demo user.",
                "unit-test-temporary"
        ));

        assertThat(response.vectorStoreMode()).isEqualTo("temporary-memory");
        assertThat(response.chunkCount()).isGreaterThan(0);
        assertThat(milvusService.count()).isEqualTo(persistentCountBefore);

        assertThat(retrievalService.search(owner, "What says alpha-bravo-charlie?", 3, "temporary"))
                .isNotEmpty();
        assertThat(retrievalService.search(owner, "What says alpha-bravo-charlie?", 3, "all"))
                .isNotEmpty();
        assertThat(retrievalService.search(owner, "What says alpha-bravo-charlie?", 3, "persistent"))
                .isEmpty();
        assertThat(retrievalService.search(otherUser, "What says alpha-bravo-charlie?", 3, "temporary"))
                .isEmpty();

        temporaryKnowledgeService.clearForUser(owner);
        assertThat(retrievalService.search(owner, "What says alpha-bravo-charlie?", 3, "temporary"))
                .isEmpty();
    }

    @Test
    void shouldReturnKnowledgeEmptyMessageWhenNoChunksAreFound() {
        String userId = "temporary-empty-user";
        temporaryKnowledgeService.clearForUser(userId);

        var response = ragChatService.chat(userId, "什么是飞天？", 3, "temporary");

        assertThat(response.retrievedChunks()).isEmpty();
        assertThat(response.citations()).isEmpty();
        assertThat(response.mode()).isEqualTo("rag-no-context");
        assertThat(response.answer()).contains("现有知识库没有检索到");
        assertThat(response.answer()).doesNotContain("本地演示模式");
    }
}
