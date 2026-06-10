package com.springai.chatsys.prompt;

import com.springai.chatsys.domain.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RagPromptBuilder {

    public String build(String question, List<RetrievedChunk> chunks) {
        String context = chunks.stream()
                .map(chunk -> "[" + chunk.chunkId() + "] " + chunk.content())
                .collect(Collectors.joining("\n\n"));
        return """
                You are a RAG question answering assistant.
                Answer only according to the context. If the context is insufficient, say that the current knowledge base cannot confirm the answer.

                Context:
                %s

                Question:
                %s

                Answer in Chinese and keep citations traceable.
                """.formatted(context, question);
    }
}
