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
@Table(name = "user_suggestion_cache")
public class UserSuggestionCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, length = 128)
    private String userKey;

    @Column(name = "suggestions_json", nullable = false, columnDefinition = "TEXT")
    private String suggestionsJson;

    @Column(name = "knowledge_version", length = 128)
    private String knowledgeVersion;

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

    public String getSuggestionsJson() {
        return suggestionsJson;
    }

    public String getKnowledgeVersion() {
        return knowledgeVersion;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public void setSuggestionsJson(String suggestionsJson) {
        this.suggestionsJson = suggestionsJson;
    }

    public void setKnowledgeVersion(String knowledgeVersion) {
        this.knowledgeVersion = knowledgeVersion;
    }
}
