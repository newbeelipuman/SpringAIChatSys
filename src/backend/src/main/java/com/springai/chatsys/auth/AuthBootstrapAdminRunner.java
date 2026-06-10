package com.springai.chatsys.auth;

import com.springai.chatsys.mysql.service.MysqlAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mysql", name = "enabled", havingValue = "true")
public class AuthBootstrapAdminRunner implements ApplicationRunner {

    private final MysqlAuthService mysqlAuthService;
    private final String username;
    private final String password;

    public AuthBootstrapAdminRunner(
            MysqlAuthService mysqlAuthService,
            @Value("${auth.bootstrap-admin.username:}") String username,
            @Value("${auth.bootstrap-admin.password:}") String password
    ) {
        this.mysqlAuthService = mysqlAuthService;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        mysqlAuthService.bootstrapAdmin(username, password);
    }
}
