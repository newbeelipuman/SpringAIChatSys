package com.springai.chatsys.mysql.repository;

import com.springai.chatsys.mysql.entity.IngestHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngestHistoryRepository extends JpaRepository<IngestHistoryEntity, Long> {

    List<IngestHistoryEntity> findByUserKeyOrderByCreatedAtDesc(String userKey);

    long countByUserKey(String userKey);

    void deleteByUserKey(String userKey);

    void deleteByUserKeyAndDocIdIn(String userKey, List<String> docIds);
}
