package com.springai.chatsys.mysql.repository;

import com.springai.chatsys.mysql.entity.KnowledgePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgePermissionRepository extends JpaRepository<KnowledgePermissionEntity, Long> {

    List<KnowledgePermissionEntity> findByUserKeyOrderByCreatedAtDesc(String userKey);

    List<KnowledgePermissionEntity> findByDocIdOrderByCreatedAtDesc(String docId);

    boolean existsByUserKeyAndDocIdAndPermissionType(String userKey, String docId, String permissionType);

    long countByUserKey(String userKey);
}
