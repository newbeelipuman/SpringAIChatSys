package com.springai.chatsys;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringAiChatSysApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiChatSysApplication.class, args);
    }
}
