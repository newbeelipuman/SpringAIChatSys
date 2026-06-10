package com.springai.chatsys.mysql.service;

import com.springai.chatsys.mysql.entity.KnowledgePermissionEntity;
import com.springai.chatsys.mysql.config.MysqlProperties;
import com.springai.chatsys.mysql.repository.KnowledgePermissionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

@Service
@ConditionalOnProperty(prefix = "mysql", name = "enabled", havingValue = "true")
public class MysqlPermissionService {

    public static final String PERMISSION_OWNER = "owner";
    public static final String PERMISSION_READ = "read";

    private final KnowledgePermissionRepository knowledgePermissionRepository;
    private final DataSource dataSource;
    private final MysqlProperties mysqlProperties;
    private volatile String lastError;

    public MysqlPermissionService(
            KnowledgePermissionRepository knowledgePermissionRepository,
            DataSource dataSource,
            MysqlProperties mysqlProperties
    ) {
        this.knowledgePermissionRepository = knowledgePermissionRepository;
        this.dataSource = dataSource;
        this.mysqlProperties = mysqlProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void grantOwnerAndRead(String userKey, String docId) {
        try {
            if (!permissionTableAvailable()) {
                return;
            }
            grantIfMissing(userKey, docId, PERMISSION_OWNER);
            grantIfMissing(userKey, docId, PERMISSION_READ);
            lastError = null;
        } catch (RuntimeException ex) {
            recordFailure(ex);
        }
    }

    @Transactional(readOnly = true)
    public long countForUser(String userKey) {
        try {
            long count = knowledgePermissionRepository.countByUserKey(userKey);
            lastError = null;
            return count;
        } catch (RuntimeException ex) {
            recordFailure(ex);
            return 0;
        }
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

    private void grantIfMissing(String userKey, String docId, String permissionType) {
        if (knowledgePermissionRepository.existsByUserKeyAndDocIdAndPermissionType(userKey, docId, permissionType)) {
            return;
        }
        KnowledgePermissionEntity entity = new KnowledgePermissionEntity();
        entity.setUserKey(userKey);
        entity.setDocId(docId);
        entity.setPermissionType(permissionType);
        knowledgePermissionRepository.save(entity);
    }

    private boolean permissionTableAvailable() {
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData().getTables(mysqlProperties.getDatabase(), null, "knowledge_permission", null)) {
            return resultSet.next();
        } catch (Exception ex) {
            recordFailure(ex);
            return false;
        }
    }
}
