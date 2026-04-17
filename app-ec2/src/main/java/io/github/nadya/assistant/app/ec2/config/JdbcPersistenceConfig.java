package io.github.nadya.assistant.app.ec2.config;

import com.zaxxer.hikari.HikariDataSource;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.JdbcAuditAdapter;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.JdbcConversationStateAdapter;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.JdbcIdempotencyAdapter;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.JdbcPersistenceSchemaInitializer;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.JdbcPersistenceSettings;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.JdbcUserContextAdapter;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.PersistenceJsonMapper;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantAppProperties;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantPersistenceJdbcProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@Conditional(JdbcPersistenceEnabledCondition.class)
public class JdbcPersistenceConfig {

    @Bean(destroyMethod = "close")
    DataSource assistantDataSource(AssistantPersistenceJdbcProperties jdbcProperties) {
        if (jdbcProperties.url().isBlank()) {
            throw new IllegalStateException("JDBC persistence mode requires assistant.persistence.jdbc.url");
        }

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcProperties.url());
        dataSource.setUsername(jdbcProperties.username());
        dataSource.setPassword(jdbcProperties.password());
        dataSource.setDriverClassName(jdbcProperties.driverClassName());
        dataSource.setMaximumPoolSize(jdbcProperties.poolMaxSize());
        dataSource.setMinimumIdle(jdbcProperties.poolMinIdle());
        dataSource.setConnectionTimeout(jdbcProperties.connectionTimeoutMs());
        dataSource.setPoolName("assistant-jdbc");
        return dataSource;
    }

    @Bean
    JdbcPersistenceSettings jdbcPersistenceSettings(
            AssistantPersistenceJdbcProperties jdbcProperties,
            AssistantAppProperties appProperties
    ) {
        return new JdbcPersistenceSettings(
                jdbcProperties.schema(),
                appProperties.idempotencyTtl()
        );
    }

    @Bean
    PersistenceJsonMapper persistenceJsonMapper() {
        return new PersistenceJsonMapper();
    }

    @Bean(initMethod = "initialize")
    JdbcPersistenceSchemaInitializer jdbcPersistenceSchemaInitializer(
            DataSource assistantDataSource,
            JdbcPersistenceSettings jdbcPersistenceSettings
    ) {
        return new JdbcPersistenceSchemaInitializer(assistantDataSource, jdbcPersistenceSettings);
    }

    @Bean
    JdbcUserContextAdapter jdbcUserContextAdapter(
            DataSource assistantDataSource,
            JdbcPersistenceSettings jdbcPersistenceSettings,
            PersistenceJsonMapper persistenceJsonMapper
    ) {
        return new JdbcUserContextAdapter(assistantDataSource, jdbcPersistenceSettings, persistenceJsonMapper);
    }

    @Bean
    JdbcConversationStateAdapter jdbcConversationStateAdapter(
            DataSource assistantDataSource,
            JdbcPersistenceSettings jdbcPersistenceSettings,
            PersistenceJsonMapper persistenceJsonMapper
    ) {
        return new JdbcConversationStateAdapter(assistantDataSource, jdbcPersistenceSettings, persistenceJsonMapper);
    }

    @Bean
    JdbcIdempotencyAdapter jdbcIdempotencyAdapter(
            DataSource assistantDataSource,
            JdbcPersistenceSettings jdbcPersistenceSettings
    ) {
        return new JdbcIdempotencyAdapter(assistantDataSource, jdbcPersistenceSettings);
    }

    @Bean
    JdbcAuditAdapter jdbcAuditAdapter(
            DataSource assistantDataSource,
            JdbcPersistenceSettings jdbcPersistenceSettings
    ) {
        return new JdbcAuditAdapter(assistantDataSource, jdbcPersistenceSettings);
    }
}
