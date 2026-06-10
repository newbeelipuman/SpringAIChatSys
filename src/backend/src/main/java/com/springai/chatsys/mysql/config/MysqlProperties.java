package com.springai.chatsys.mysql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mysql")
public class MysqlProperties {

    private boolean enabled = false;
    private String host = "localhost";
    private int port = 3306;
    private String database = "spring_ai_chat_sys";
    private String username = "root";
    private String password = "";
    private String ddlAuto = "none";
    private boolean historyReadEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDdlAuto() {
        return ddlAuto;
    }

    public void setDdlAuto(String ddlAuto) {
        this.ddlAuto = ddlAuto;
    }

    public boolean isHistoryReadEnabled() {
        return historyReadEnabled;
    }

    public void setHistoryReadEnabled(boolean historyReadEnabled) {
        this.historyReadEnabled = historyReadEnabled;
    }

    public String jdbcUrl() {
        return "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
                .formatted(host, port, database);
    }
}
