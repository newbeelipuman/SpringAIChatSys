package com.springai.chatsys.auth;

import com.springai.chatsys.mysql.service.MysqlAuthService;
import com.springai.chatsys.service.DemoUserContext;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class IdentityResolver {

    private final Optional<MysqlAuthService> mysqlAuthService;

    public IdentityResolver(Optional<MysqlAuthService> mysqlAuthService) {
        this.mysqlAuthService = mysqlAuthService;
    }

    public CurrentUser resolve(String authorizationHeader, String demoUserHeader) {
        Optional<CurrentUser> authenticated = tokenFrom(authorizationHeader)
                .flatMap(token -> mysqlAuthService.flatMap(service -> service.currentUser(token)));
        if (authenticated.isPresent()) {
            return authenticated.get();
        }

        if (demoUserHeader != null && !demoUserHeader.isBlank()) {
            return CurrentUser.demo(DemoUserContext.resolve(demoUserHeader), "x-demo-user");
        }
        return CurrentUser.demo(DemoUserContext.DEFAULT_USER_ID, "default-demo-user");
    }

    public Optional<String> tokenFrom(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Optional.empty();
        }
        String value = authorizationHeader.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = value.substring(7).trim();
            return token.isBlank() ? Optional.empty() : Optional.of(token);
        }
        return Optional.empty();
    }
}
