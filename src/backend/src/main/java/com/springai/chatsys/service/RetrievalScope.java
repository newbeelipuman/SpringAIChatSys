package com.springai.chatsys.service;

public enum RetrievalScope {
    PERSISTENT,
    TEMPORARY,
    ALL;

    public static RetrievalScope from(String value) {
        if (value == null || value.isBlank()) {
            return PERSISTENT;
        }
        return switch (value.trim().toLowerCase()) {
            case "temporary" -> TEMPORARY;
            case "all" -> ALL;
            default -> PERSISTENT;
        };
    }

    public String wireValue() {
        return name().toLowerCase();
    }
}
