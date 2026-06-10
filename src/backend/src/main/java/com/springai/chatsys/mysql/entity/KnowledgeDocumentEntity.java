package com.springai.chatsys.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, length = 128)
    private String userKey;

    @Column(name = "doc_id", nullable = false, length = 160)
    private String docId;

    @Column(name = "document_name", length = 255)
    private String documentName;

    @Column(name = "source", length = 255)
    private String source;

    @Column(name = "storage_scope", nullable = false, length = 32)
    private String storageScope;

    @Column(name = "vector_store_mode", length = 64)
    private String vectorStoreMode;

    @Column(name = "chunk_count")
    private int chunkCount;

    @Column(name = "embedding_dimensions")
    private int embeddingDimensions;

    @Column(name = "content_preview", columnDefinition = "TEXT")
    private String contentPreview;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUserKey() {
        return userKey;
    }

    public String getDocId() {
        return docId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public String getSource() {
        return source;
    }

    public String getStorageScope() {
        return storageScope;
    }

    public String getVectorStoreMode() {
        return vectorStoreMode;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public String getContentPreview() {
        return contentPreview;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setStorageScope(String storageScope) {
        this.storageScope = storageScope;
    }

    public void setVectorStoreMode(String vectorStoreMode) {
        this.vectorStoreMode = vectorStoreMode;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public void setContentPreview(String contentPreview) {
        this.contentPreview = contentPreview;
    }
}
