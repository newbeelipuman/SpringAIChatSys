package com.springai.chatsys.service;

import com.springai.chatsys.domain.RetrievedChunk;
import com.springai.chatsys.dto.ChatResponse;
import com.springai.chatsys.dto.CitationDTO;
import com.springai.chatsys.dto.RetrievedChunkDTO;
import com.springai.chatsys.prompt.RagPromptBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagChatService {

    private static final String EMPTY_KNOWLEDGE_MODE = "rag-no-context";

    private final RetrievalService retrievalService;
    private final RagPromptBuilder promptBuilder;
    private final LlmGateway llmGateway;

    public RagChatService(RetrievalService retrievalService, RagPromptBuilder promptBuilder, LlmGateway llmGateway) {
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.llmGateway = llmGateway;
    }

    public ChatResponse chat(String question, Integer topK) {
        return chat(DemoUserContext.DEFAULT_USER_ID, question, topK, RetrievalScope.PERSISTENT.wireValue());
    }

    public ChatResponse chat(String userId, String question, Integer topK, String scope) {
        long start = System.currentTimeMillis();
        List<RetrievedChunk> chunks = retrievalService.search(userId, question, topK, scope);
        String answer = chunks.isEmpty()
                ? "现有知识库没有检索到与该问题相关的信息。可以先录入临时知识或保存到知识库后再提问。"
                : llmGateway.generate(promptBuilder.build(question, chunks));
        String mode = chunks.isEmpty() ? EMPTY_KNOWLEDGE_MODE : llmGateway.mode();
        return new ChatResponse(
                question,
                answer,
                chunks.stream().map(this::toCitation).toList(),
                chunks.stream().map(this::toDto).toList(),
                System.currentTimeMillis() - start,
                mode
        );
    }

    private CitationDTO toCitation(RetrievedChunk chunk) {
        return new CitationDTO(chunk.chunkId(), chunk.docId(), chunk.documentName(), chunk.source(), chunk.score());
    }

    private RetrievedChunkDTO toDto(RetrievedChunk chunk) {
        return new RetrievedChunkDTO(chunk.chunkId(), chunk.docId(), chunk.documentName(), chunk.content(), chunk.source(), chunk.score());
    }
}
