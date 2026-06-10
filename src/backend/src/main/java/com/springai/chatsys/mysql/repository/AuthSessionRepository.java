package com.springai.chatsys.mysql.repository;

import com.springai.chatsys.mysql.entity.AuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, Long> {

    Optional<AuthSessionEntity> findByTokenHash(String tokenHash);

    List<AuthSessionEntity> findByUserKeyAndRevokedFalse(String userKey);

    long countByUserKeyAndRevokedFalse(String userKey);
}
