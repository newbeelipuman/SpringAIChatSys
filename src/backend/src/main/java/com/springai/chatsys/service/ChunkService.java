package com.springai.chatsys.service;

import com.springai.chatsys.config.RagProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkService {

    private static final int MIN_STANDALONE_PARAGRAPH_LENGTH = 80;

    private final RagProperties properties;

    public ChunkService(RagProperties properties) {
        this.properties = properties;
    }

    public List<String> split(String content) {
        String normalized = normalize(content);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> paragraphChunks = splitBySemanticBlocks(normalized);
        List<String> chunks = new ArrayList<>();
        for (String paragraph : paragraphChunks) {
            chunks.addAll(splitByLength(paragraph));
        }
        return chunks;
    }

    private static String normalize(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static List<String> splitByParagraph(String content) {
        String[] paragraphs = content.split("\\n\\s*\\n");
        List<String> result = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String value = paragraph.trim();
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    private static List<String> splitBySemanticBlocks(String content) {
        List<String> paragraphs = splitByParagraph(content);
        boolean hasMarkdownSections = paragraphs.stream().anyMatch(ChunkService::isSectionHeading);
        if (!hasMarkdownSections) {
            return mergeShortParagraphs(paragraphs);
        }

        List<String> blocks = new ArrayList<>();
        StringBuilder currentSection = null;

        for (String paragraph : paragraphs) {
            if (isSectionHeading(paragraph)) {
                if (currentSection != null) {
                    blocks.add(currentSection.toString());
                }
                currentSection = new StringBuilder(paragraph);
                continue;
            }

            if (currentSection == null) {
                blocks.add(paragraph);
            } else {
                currentSection.append("\n\n").append(paragraph);
            }
        }

        if (currentSection != null) {
            blocks.add(currentSection.toString());
        }

        return mergeShortParagraphs(blocks);
    }

    private static boolean isSectionHeading(String paragraph) {
        return paragraph.matches("^##\\s+\\S.*");
    }

    private static List<String> mergeShortParagraphs(List<String> paragraphs) {
        List<String> result = new ArrayList<>();
        String pending = "";

        for (String paragraph : paragraphs) {
            String current = paragraph.trim();
            if (current.isBlank()) {
                continue;
            }

            if (!pending.isBlank()) {
                current = pending + "\n" + current;
                pending = "";
            }

            if (isShortStandalone(current)) {
                pending = current;
                continue;
            }

            result.add(current);
        }

        if (!pending.isBlank()) {
            if (result.isEmpty()) {
                result.add(pending);
            } else {
                int lastIndex = result.size() - 1;
                result.set(lastIndex, result.get(lastIndex) + "\n" + pending);
            }
        }

        return result;
    }

    private static boolean isShortStandalone(String paragraph) {
        String compact = paragraph.replaceAll("\\s+", "");
        if (compact.length() >= MIN_STANDALONE_PARAGRAPH_LENGTH) {
            return false;
        }
        return compact.endsWith(":")
                || compact.endsWith("：")
                || compact.startsWith("#")
                || compact.matches("^\\d+[.、].*");
    }

    private List<String> splitByLength(String text) {
        int chunkSize = Math.max(100, properties.getChunkSize());
        int overlap = Math.max(0, Math.min(properties.getChunkOverlap(), chunkSize / 2));
        if (text.length() <= chunkSize) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end).trim());
            if (end == text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }
}
