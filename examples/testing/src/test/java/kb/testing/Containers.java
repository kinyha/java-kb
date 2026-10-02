package kb.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Один контейнер PostgreSQL на контекст; @ServiceConnection сам задаёт spring.datasource.*.
@TestConfiguration(proxyBeanMethods = false)
class Containers {
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
