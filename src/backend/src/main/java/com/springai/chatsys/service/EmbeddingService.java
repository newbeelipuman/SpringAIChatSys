package com.springai.chatsys.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    public static final int LOCAL_DIMENSIONS = 384;

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    public EmbeddingService(ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
    }

    public List<Double> embed(String text) {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel != null) {
            float[] vector = embeddingModel.embed(text);
            List<Double> result = new ArrayList<>(vector.length);
            for (float item : vector) {
                result.add((double) item);
            }
            return normalize(result);
        }
        return localEmbedding(text);
    }

    public String mode() {
        return embeddingModelProvider.getIfAvailable() == null ? "local-hash" : "spring-ai";
    }

    private static List<Double> localEmbedding(String text) {
        double[] vector = new double[LOCAL_DIMENSIONS];
        String normalized = text == null ? "" : text.toLowerCase();
        String[] tokens = normalized.split("[\\s\\p{P}\\p{S}]+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            addFeature(vector, "token:" + token, 1.0d);
            addCjkFeatures(vector, token);
        }

        List<Double> result = new ArrayList<>(LOCAL_DIMENSIONS);
        for (double item : vector) {
            result.add(item);
        }
        return normalize(result);
    }

    private static void addCjkFeatures(double[] vector, String token) {
        StringBuilder cjk = new StringBuilder();
        for (int offset = 0; offset < token.length(); ) {
            int codePoint = token.codePointAt(offset);
            if (isCjk(codePoint)) {
                cjk.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        if (cjk.isEmpty()) {
            return;
        }

        String text = cjk.toString();
        for (int index = 0; index < text.length(); index++) {
            addFeature(vector, "cjk1:" + text.charAt(index), 0.7d);
        }
        for (int index = 0; index + 1 < text.length(); index++) {
            addFeature(vector, "cjk2:" + text.substring(index, index + 2), 1.6d);
        }
        for (int index = 0; index + 2 < text.length(); index++) {
            addFeature(vector, "cjk3:" + text.substring(index, index + 3), 1.0d);
        }
    }

    private static boolean isCjk(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    private static void addFeature(double[] vector, String value, double weight) {
        int bucket = Math.floorMod(hash(value), LOCAL_DIMENSIONS);
        vector[bucket] += weight;
    }

    private static int hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16) | ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
        } catch (NoSuchAlgorithmException ex) {
            return value.hashCode();
        }
    }

    private static List<Double> normalize(List<Double> vector) {
        double sum = 0.0d;
        for (double item : vector) {
            sum += item * item;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0.0d) {
            return vector;
        }
        List<Double> normalized = new ArrayList<>(vector.size());
        for (double item : vector) {
            normalized.add(item / norm);
        }
        return normalized;
    }
}
