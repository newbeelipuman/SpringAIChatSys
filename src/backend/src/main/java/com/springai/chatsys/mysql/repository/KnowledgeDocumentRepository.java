package com.springai.chatsys.mysql.repository;

import com.springai.chatsys.mysql.entity.KnowledgeDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, Long> {

    List<KnowledgeDocumentEntity> findByUserKeyOrderByCreatedAtDesc(String userKey);

    List<KnowledgeDocumentEntity> findByUserKeyAndIdIn(String userKey, List<Long> ids);

    List<KnowledgeDocumentEntity> findAllByOrderByCreatedAtDesc();

    long countByUserKey(String userKey);

    void deleteByUserKey(String userKey);

    void deleteByUserKeyAndIdIn(String userKey, List<Long> ids);
}
