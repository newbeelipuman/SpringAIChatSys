package com.springai.chatsys.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LlmGateway {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final String apiKey;
    private final String chatModelName;

    public LlmGateway(
            ObjectProvider<ChatModel> chatModelProvider,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:}") String chatModelName
    ) {
        this.chatModelProvider = chatModelProvider;
        this.apiKey = apiKey;
        this.chatModelName = chatModelName;
    }

    public String generate(String prompt) {
        if (!StringUtils.hasText(apiKey)) {
            return localDemoAnswer(prompt);
        }
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return localDemoAnswer(prompt);
        }
        return ChatClient.builder(chatModel)
                .build()
                .prompt()
                .user(prompt)
                .call()
                .content();
    }

    public String generateWithExternalModel(String prompt) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("外部大模型 API Key 未配置，请确认已设置 OPENAI_API_KEY 或 DEEPSEEK_API_KEY，并使用对应 profile 启动后端。");
        }
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new IllegalStateException("外部大模型 ChatModel 未加载，请确认 spring.ai.model.chat=openai 或已启用 deepseek/openai profile。");
        }
        return ChatClient.builder(chatModel)
                .build()
                .prompt()
                .user(prompt)
                .call()
                .content();
    }

    public String mode() {
        return externalConfigured() ? "spring-ai-openai" : "local-demo";
    }

    public boolean externalConfigured() {
        return StringUtils.hasText(apiKey) && chatModelProvider.getIfAvailable() != null;
    }

    public String chatModel() {
        return chatModelName;
    }

    public boolean apiKeyConfigured() {
        return StringUtils.hasText(apiKey);
    }

    public boolean chatModelAvailable() {
        return chatModelProvider.getIfAvailable() != null;
    }

    private String localDemoAnswer(String prompt) {
        String question = extractSection(prompt, "Question:", "Answer in Chinese");
        String context = extractSection(prompt, "Context:", "Question:");
        if (!StringUtils.hasText(question)) {
            question = prompt == null ? "" : prompt.trim();
        }

        if (StringUtils.hasText(context)) {
            String evidence = summarizeContext(context);
            if (StringUtils.hasText(evidence)) {
                return """
                        本地演示模式回答：已完成 RAG 检索，并根据命中的知识片段回答问题“%s”。

                        参考依据：
                        %s

                        当前未配置外部大模型，因此答案由本地规则生成；配置 Spring AI 外部模型后，会基于同一批引用片段生成更自然的完整回答。
                        """.formatted(question.trim(), evidence);
            }
        }

        return "当前未配置外部大模型，系统处于本地演示模式。已收到问题：“%s”。配置 Spring AI 外部模型后可生成完整自然语言回答。"
                .formatted(question.trim());
    }

    private static String extractSection(String text, String startMarker, String endMarker) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int start = text.indexOf(startMarker);
        if (start < 0) {
            return "";
        }
        int contentStart = start + startMarker.length();
        int end = text.indexOf(endMarker, contentStart);
        if (end < 0) {
            end = text.length();
        }
        return text.substring(contentStart, end).trim();
    }

    private static String summarizeContext(String context) {
        StringBuilder builder = new StringBuilder();
        String[] blocks = context.split("\\n\\s*\\n");
        int count = 0;
        for (String block : blocks) {
            String cleaned = block.replaceAll("\\s+", " ").trim();
            if (!StringUtils.hasText(cleaned)) {
                continue;
            }
            if (cleaned.length() > 220) {
                cleaned = cleaned.substring(0, 220) + "...";
            }
            builder.append("- ").append(cleaned).append('\n');
            count++;
            if (count >= 3) {
                break;
            }
        }
        return builder.toString().trim();
    }
}
