package com.springai.chatsys.controller;

import com.springai.chatsys.dto.AskRequest;
import com.springai.chatsys.dto.AskResponse;
import com.springai.chatsys.dto.HealthResponse;
import com.springai.chatsys.auth.IdentityResolver;
import com.springai.chatsys.service.DemoAuditService;
import com.springai.chatsys.service.EmbeddingService;
import com.springai.chatsys.service.LlmGateway;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/llm")
public class LlmController {

    private final LlmGateway llmGateway;
    private final EmbeddingService embeddingService;
    private final String chatModel;
    private final String embeddingModel;
    private final String baseUrl;
    private final Environment environment;
    private final DemoAuditService demoAuditService;
    private final IdentityResolver identityResolver;

    public LlmController(
            LlmGateway llmGateway,
            EmbeddingService embeddingService,
            DemoAuditService demoAuditService,
            IdentityResolver identityResolver,
            Environment environment,
            @Value("${spring.ai.openai.chat.options.model}") String chatModel,
            @Value("${spring.ai.openai.embedding.options.model}") String embeddingModel,
            @Value("${spring.ai.openai.base-url}") String baseUrl
    ) {
        this.llmGateway = llmGateway;
        this.embeddingService = embeddingService;
        this.demoAuditService = demoAuditService;
        this.identityResolver = identityResolver;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.baseUrl = baseUrl;
        this.environment = environment;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", Map.of(
                "chatMode", llmGateway.mode(),
                "externalChatConfigured", llmGateway.externalConfigured(),
                "apiKeyConfigured", llmGateway.apiKeyConfigured(),
                "chatModelAvailable", llmGateway.chatModelAvailable(),
                "embeddingMode", embeddingService.mode()
        ));
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "baseUrl", baseUrl,
                "chatModel", chatModel,
                "embeddingModel", embeddingModel,
                "chatMode", llmGateway.mode(),
                "externalChatConfigured", llmGateway.externalConfigured(),
                "apiKeyConfigured", llmGateway.apiKeyConfigured(),
                "chatModelAvailable", llmGateway.chatModelAvailable(),
                "activeProfiles", environment.getActiveProfiles(),
                "embeddingMode", embeddingService.mode()
        );
    }

    @PostMapping("/ask")
    public AskResponse ask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String userId,
            @Valid @RequestBody AskRequest request
    ) {
        long start = System.currentTimeMillis();
        String answer = llmGateway.generateWithExternalModel(request.question());
        AskResponse response = new AskResponse(answer, llmGateway.mode(), System.currentTimeMillis() - start);
        demoAuditService.recordDirectAsk(identityResolver.resolve(authorization, userId).userKey(), request.question(), response);
        return response;
    }
}
