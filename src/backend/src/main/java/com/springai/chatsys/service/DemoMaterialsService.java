package com.springai.chatsys.service;

import com.springai.chatsys.dto.DemoMaterialDTO;
import com.springai.chatsys.dto.IngestMaterialsItemDTO;
import com.springai.chatsys.dto.IngestMaterialsResponse;
import com.springai.chatsys.dto.IngestRequest;
import com.springai.chatsys.dto.IngestResponse;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class DemoMaterialsService {

    private static final String MATERIALS_PATTERN = "classpath*:demo-materials/*.*";

    private final ResourcePatternResolver resourcePatternResolver;
    private final DocumentIngestService documentIngestService;

    public DemoMaterialsService(ResourcePatternResolver resourcePatternResolver, DocumentIngestService documentIngestService) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.documentIngestService = documentIngestService;
    }

    public List<DemoMaterialDTO> listMaterials() {
        Resource[] resources = resolveMaterials();
        return Arrays.stream(resources)
                .sorted(Comparator.comparing(resource -> safeFilename(resource.getFilename())))
                .map(this::toMaterialDto)
                .toList();
    }

    public IngestMaterialsResponse ingestAll() {
        long start = System.currentTimeMillis();
        Resource[] resources = resolveMaterials();
        List<IngestMaterialsItemDTO> items = new ArrayList<>();
        int success = 0;
        int fail = 0;
        int totalChunks = 0;
        String vectorStoreMode = null;

        List<Resource> sorted = Arrays.stream(resources)
                .sorted(Comparator.comparing(resource -> safeFilename(resource.getFilename())))
                .toList();

        for (Resource resource : sorted) {
            String filename = safeFilename(resource.getFilename());
            try {
                String content = readUtf8(resource);
                IngestResponse ingest = documentIngestService.ingest(new IngestRequest(
                        toDocId(filename),
                        toDocumentName(filename),
                        content,
                        toSource(filename)
                ));
                vectorStoreMode = ingest.vectorStoreMode();
                totalChunks += ingest.chunkCount();
                items.add(new IngestMaterialsItemDTO(filename, ingest, null));
                success++;
            } catch (Exception ex) {
                items.add(new IngestMaterialsItemDTO(filename, null, safeErrorMessage(ex)));
                fail++;
            }
        }

        return new IngestMaterialsResponse(
                items,
                success,
                fail,
                totalChunks,
                vectorStoreMode,
                System.currentTimeMillis() - start
        );
    }

    private Resource[] resolveMaterials() {
        try {
            return resourcePatternResolver.getResources(MATERIALS_PATTERN);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to resolve demo materials: " + ex.getMessage(), ex);
        }
    }

    private DemoMaterialDTO toMaterialDto(Resource resource) {
        String filename = safeFilename(resource.getFilename());
        MaterialMeta meta = materialMeta(filename);
        return new DemoMaterialDTO(
                filename,
                toDocId(filename),
                toDocumentName(filename),
                meta.displayName(),
                meta.category(),
                meta.description(),
                meta.sampleQuestions(),
                toSource(filename),
                safeContentLength(resource)
        );
    }

    private static MaterialMeta materialMeta(String filename) {
        return switch (safeFilename(filename)) {
            case "01-system-overview.md" -> new MaterialMeta(
                    "01 系统总览",
                    "架构说明",
                    "介绍 Spring Boot + Spring AI + Vue 的 RAG 演示系统定位、核心模块和答辩展示要点。",
                    List.of("系统由哪些模块组成？", "RAG 系统的主要流程是什么？")
            );
            case "02-rag-workflow.md" -> new MaterialMeta(
                    "02 RAG 主流程",
                    "流程说明",
                    "说明文档写入、文本分块、向量化、TopK 检索、上下文拼接和答案生成的完整链路。",
                    List.of("RAG 系统的主要流程是什么？", "为什么需要返回引用来源？")
            );
            case "03-vectorstore-memory-vs-milvus.md" -> new MaterialMeta(
                    "03 向量库对比",
                    "存储设计",
                    "对比内存向量库和 Milvus 的使用场景，说明演示模式与真实落地模式的差异。",
                    List.of("Milvus 和内存向量库有什么区别？", "为什么内存向量库重启后需要重新导入？")
            );
            case "04-frontend-fields.md" -> new MaterialMeta(
                    "04 前端字段展示",
                    "界面说明",
                    "列出问答页需要展示的 answer、citations、retrievedChunks、score、elapsedMs 等字段。",
                    List.of("前端页面需要展示哪些字段？", "citations 和 retrievedChunks 分别有什么作用？")
            );
            case "05-enterprise-kb-simulation.md" -> new MaterialMeta(
                    "05 模拟企业制度知识库",
                    "综合素材",
                    "脱敏抽象的企业制度与流程素材，覆盖差旅报销、采购审批、账号权限、客户工单和知识库运营。",
                    List.of("员工出差后多久需要提交报销？", "一级客户工单多久响应？", "软件服务采购为什么需要安全评估？")
            );
            default -> new MaterialMeta(
                    toDocumentName(filename),
                    "自定义素材",
                    "按 NN-topic.md 命名的内置演示素材，可用于写入、检索和 RAG 问答测试。",
                    List.of()
            );
        };
    }

    private static String readUtf8(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    private static long safeContentLength(Resource resource) {
        try {
            return resource.contentLength();
        } catch (IOException ex) {
            return -1L;
        }
    }

    private static String toSource(String filename) {
        return "demo-materials/" + safeFilename(filename);
    }

    private static String toDocumentName(String filename) {
        String base = safeFilename(filename);
        int dot = base.lastIndexOf('.');
        String name = dot > 0 ? base.substring(0, dot) : base;
        return name.replace('_', ' ').trim();
    }

    private static String toDocId(String filename) {
        String base = safeFilename(filename);
        int dot = base.lastIndexOf('.');
        String name = dot > 0 ? base.substring(0, dot) : base;
        String slug = name
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9\\-_.]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "demo-material" : "demo-" + slug;
    }

    private static String safeFilename(String filename) {
        return filename == null ? "unknown" : filename;
    }

    private static String safeErrorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return ex.getClass().getSimpleName();
    }

    private record MaterialMeta(
            String displayName,
            String category,
            String description,
            List<String> sampleQuestions
    ) {
    }
}
