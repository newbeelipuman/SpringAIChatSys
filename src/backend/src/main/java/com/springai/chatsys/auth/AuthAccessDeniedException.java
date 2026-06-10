package com.springai.chatsys.auth;

public class AuthAccessDeniedException extends RuntimeException {

    public AuthAccessDeniedException(String message) {
        super(message);
    }
}
