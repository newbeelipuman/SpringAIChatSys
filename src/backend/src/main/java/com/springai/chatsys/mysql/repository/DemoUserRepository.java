package com.springai.chatsys.mysql.repository;

import com.springai.chatsys.mysql.entity.DemoUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DemoUserRepository extends JpaRepository<DemoUserEntity, Long> {

    Optional<DemoUserEntity> findByDemoUserKey(String demoUserKey);
}
