package com.springai.chatsys.mysql.service;

import com.springai.chatsys.mysql.entity.DemoUserEntity;
import com.springai.chatsys.mysql.repository.DemoUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "mysql", name = "enabled", havingValue = "true")
public class MysqlDemoUserService {

    private final DemoUserRepository demoUserRepository;
    private volatile String lastError;

    public MysqlDemoUserService(DemoUserRepository demoUserRepository) {
        this.demoUserRepository = demoUserRepository;
    }

    @Transactional
    public DemoUserEntity ensureUser(String userKey) {
        try {
            DemoUserEntity user = demoUserRepository.findByDemoUserKey(userKey).orElseGet(() -> {
                DemoUserEntity created = new DemoUserEntity();
                created.setDemoUserKey(userKey);
                return created;
            });
            user.setDisplayName(userKey);
            DemoUserEntity saved = demoUserRepository.save(user);
            lastError = null;
            return saved;
        } catch (RuntimeException ex) {
            recordFailure(ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public long totalUsers() {
        return demoUserRepository.count();
    }

    public String lastError() {
        return lastError;
    }

    public void recordFailure(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            lastError = ex.getClass().getSimpleName();
            return;
        }
        lastError = message.length() > 240 ? message.substring(0, 240) + "..." : message;
    }
}
