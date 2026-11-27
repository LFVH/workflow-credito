package br.com.luiz.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // No Windows, conectar numa porta fechada leva ~2 s para falhar; 500 ms deixa o teste rápido.
    properties = "spring.cloud.gateway.server.webflux.httpclient.connect-timeout=500")
class FallbackGatewayTest {

    private static final String REQUEST_ID = "X-Request-Id";
    private static final String TIPO =
        "https://github.com/LFVH/workflow-credito/blob/main/docs/problemas.md#servico-indisponivel";

    /** A "aplicação" está fora do ar: ninguém escuta na porta para onde a rota aponta. */
    @DynamicPropertySource
    static void apontarParaUmaPortaSemNinguem(DynamicPropertyRegistry registry) {
        int porta = portaLivre();
        registry.add("APP_URI", () -> "http://127.0.0.1:" + porta);
    }

    private static int portaLivre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort(); // o socket fecha ao sair do try: a porta fica sem ninguém
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Autowired
    WebTestClient http;

    @Autowired
    CircuitBreakerRegistry circuitos;

    @BeforeEach
    void circuitoFechadoEZerado() {
        circuitos.circuitBreaker("workflow").reset();
    }

    @Test
    void aplicacaoForaDoArViraProblemDetail503() {
        http.get().uri("/api/propostas")
            .header(REQUEST_ID, "teste-fallback-1")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "10")
            .expectHeader().valueEquals(REQUEST_ID, "teste-fallback-1")
            .expectBody()
            .jsonPath("$.status").isEqualTo(503)
            .jsonPath("$.title").isEqualTo("Serviço indisponível")
            .jsonPath("$.type").isEqualTo(TIPO)
            .jsonPath("$.requestId").isEqualTo("teste-fallback-1");
    }

    @Test
    void postDoGraphqlTambemRecebeOFallback() {
        http.post().uri("/graphql")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"query\":\"{ propostas(limite: 3) { id } }\"}")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectBody().jsonPath("$.title").isEqualTo("Serviço indisponível");
    }

    @Test
    void depoisDeCincoFalhasOCircuitoAbreEOGatewayNemTentaMais() {
        for (int i = 0; i < 5; i++) {
            http.get().uri("/api/propostas")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
        CircuitBreaker circuito = circuitos.circuitBreaker("workflow");
        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        http.get().uri("/api/propostas")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(circuito.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
    }
}
