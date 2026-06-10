package com.springai.chatsys.mysql.repository;

import com.springai.chatsys.mysql.entity.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

    boolean existsByUsername(String username);

    Optional<AppUserEntity> findByUsername(String username);

    Optional<AppUserEntity> findByUserKey(String userKey);

    List<AppUserEntity> findAllByOrderByCreatedAtDesc();

    boolean existsByRole(String role);

    long countByRole(String role);

    @Query("select u.role, count(u) from AppUserEntity u group by u.role")
    List<Object[]> countUsersByRole();
}
