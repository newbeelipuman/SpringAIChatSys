package com.springai.chatsys.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "question_history")
public class QuestionHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, length = 128)
    private String userKey;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer_preview", columnDefinition = "TEXT")
    private String answerPreview;

    @Column(name = "mode", length = 64)
    private String mode;

    @Column(name = "request_type", length = 64)
    private String requestType;

    @Column(name = "top_k")
    private int topK;

    @Column(name = "retrieval_scope", length = 32)
    private String retrievalScope;

    @Column(name = "retrieved_chunk_count")
    private int retrievedChunkCount;

    @Column(name = "citation_chunk_ids_json", columnDefinition = "TEXT")
    private String citationChunkIdsJson;

    @Column(name = "elapsed_ms")
    private long elapsedMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUserKey() {
        return userKey;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswerPreview() {
        return answerPreview;
    }

    public String getMode() {
        return mode;
    }

    public String getRequestType() {
        return requestType;
    }

    public int getTopK() {
        return topK;
    }

    public String getRetrievalScope() {
        return retrievalScope;
    }

    public int getRetrievedChunkCount() {
        return retrievedChunkCount;
    }

    public String getCitationChunkIdsJson() {
        return citationChunkIdsJson;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public void setAnswerPreview(String answerPreview) {
        this.answerPreview = answerPreview;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public void setRetrievalScope(String retrievalScope) {
        this.retrievalScope = retrievalScope;
    }

    public void setRetrievedChunkCount(int retrievedChunkCount) {
        this.retrievedChunkCount = retrievedChunkCount;
    }

    public void setCitationChunkIdsJson(String citationChunkIdsJson) {
        this.citationChunkIdsJson = citationChunkIdsJson;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}
