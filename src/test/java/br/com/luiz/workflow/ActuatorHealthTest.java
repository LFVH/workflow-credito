package br.com.luiz.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * O healthcheck do Docker Compose chama /actuator/health dentro do container: é contrato
 * de operação. Contexto inteiro com Postgres de verdade (Testcontainers); o Kafka não é
 * necessário porque relay e listeners ficam desligados por padrão (item 08).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ActuatorHealthTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestRestTemplate http;

    @Test
    void healthRespondeUpQuandoOBancoEstaNoAr() {
        ResponseEntity<String> resposta = http.getForEntity("/actuator/health", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void soOHealthFicaExpostoNaWeb() {
        assertThat(http.getForEntity("/actuator/env", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity("/actuator/beans", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
