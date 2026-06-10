package com.springai.chatsys.mysql.repository;

import com.springai.chatsys.mysql.entity.QuestionHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionHistoryRepository extends JpaRepository<QuestionHistoryEntity, Long> {

    List<QuestionHistoryEntity> findByUserKeyOrderByCreatedAtDesc(String userKey);

    List<QuestionHistoryEntity> findByUserKeyAndIdIn(String userKey, List<Long> ids);

    List<QuestionHistoryEntity> findAllByOrderByCreatedAtDesc();

    long countByUserKey(String userKey);

    void deleteByUserKey(String userKey);

    void deleteByUserKeyAndIdIn(String userKey, List<Long> ids);
}
