package com.springai.chatsys.mysql.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableConfigurationProperties(MysqlProperties.class)
@ConditionalOnProperty(prefix = "mysql", name = "enabled", havingValue = "true")
@EnableJpaRepositories(
        basePackages = "com.springai.chatsys.mysql.repository",
        entityManagerFactoryRef = "mysqlEntityManagerFactory",
        transactionManagerRef = "mysqlTransactionManager"
)
public class MysqlPersistenceConfig {

    @Bean
    public DataSource mysqlDataSource(MysqlProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.jdbcUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setPoolName("spring-ai-chat-mysql");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(2000);
        config.setValidationTimeout(1000);
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean mysqlEntityManagerFactory(
            DataSource mysqlDataSource,
            MysqlProperties properties
    ) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(mysqlDataSource);
        factory.setPackagesToScan("com.springai.chatsys.mysql.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(jpaProperties(properties));
        return factory;
    }

    @Bean
    public PlatformTransactionManager mysqlTransactionManager(EntityManagerFactory mysqlEntityManagerFactory) {
        return new JpaTransactionManager(mysqlEntityManagerFactory);
    }

    private Map<String, Object> jpaProperties(MysqlProperties mysqlProperties) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", mysqlProperties.getDdlAuto());
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        properties.put("hibernate.show_sql", "false");
        properties.put("hibernate.format_sql", "false");
        properties.put("hibernate.boot.allow_jdbc_metadata_access", "false");
        return properties;
    }
}
