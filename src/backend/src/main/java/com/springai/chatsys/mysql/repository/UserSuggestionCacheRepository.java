package com.springai.chatsys.mysql.repository;

import com.springai.chatsys.mysql.entity.UserSuggestionCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSuggestionCacheRepository extends JpaRepository<UserSuggestionCacheEntity, Long> {

    Optional<UserSuggestionCacheEntity> findByUserKey(String userKey);

    boolean existsByUserKey(String userKey);
}
