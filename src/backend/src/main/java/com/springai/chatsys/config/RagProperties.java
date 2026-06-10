package com.springai.chatsys.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private int topK = 4;
    private int chunkSize = 500;
    private int chunkOverlap = 80;
    private double minScore = 0.18;
    private VectorStore vectorStore = new VectorStore();

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public void setVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public static class VectorStore {
        private String mode = "memory";
        private Milvus milvus = new Milvus();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public Milvus getMilvus() {
            return milvus;
        }

        public void setMilvus(Milvus milvus) {
            this.milvus = milvus;
        }
    }

    public static class Milvus {
        private String host = "localhost";
        private int port = 19530;
        private String uri;
        private String username;
        private String password;
        private String token;
        private String databaseName = "default";
        private String collectionName = "spring_ai_chat_chunks";
        private int dimension = 384;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public void setDatabaseName(String databaseName) {
            this.databaseName = databaseName;
        }

        public String getCollectionName() {
            return collectionName;
        }

        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }
    }
}
