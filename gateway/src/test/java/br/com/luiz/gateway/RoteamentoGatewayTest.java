package br.com.luiz.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.luiz.gateway.BackendFalso.Recebida;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoteamentoGatewayTest {

    /** Faz o papel da aplicação; a rota do gateway aponta para ele via APP_URI. */
    static final BackendFalso aplicacao = BackendFalso.iniciar();

    @DynamicPropertySource
    static void apontarParaOBackendFalso(DynamicPropertyRegistry registry) {
        registry.add("APP_URI", aplicacao::url);
    }

    @AfterAll
    static void desligarBackendFalso() {
        aplicacao.close();
    }

    @Autowired
    WebTestClient http;

    @BeforeEach
    void esquecerRequisicoesAnteriores() {
        aplicacao.esquecer();
    }

    // ---------------------------------------------------------------- rotas

    @Test
    void encaminhaApiComCaminhoEQueryIntactos() {
        http.get().uri("/api/propostas?limite=5")
            .exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.origem").isEqualTo("backend-falso");

        Recebida recebida = aplicacao.proxima();
        assertThat(recebida.metodo()).isEqualTo("GET");
        assertThat(recebida.caminho()).isEqualTo("/api/propostas?limite=5");
    }

    @Test
    void encaminhaPostDoGraphqlComOCorpo() {
        String consulta = """
            {"query":"{ propostas(limite: 3) { id status } }"}""";

        http.post().uri("/graphql")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(consulta)
            .exchange()
            .expectStatus().isOk();

        Recebida recebida = aplicacao.proxima();
        assertThat(recebida.metodo()).isEqualTo("POST");
        assertThat(recebida.caminho()).isEqualTo("/graphql");
        assertThat(recebida.corpo()).isEqualTo(consulta);
    }

    @Test
    void encaminhaAPaginaDoGraphiql() {
        http.get().uri("/graphiql?path=/graphql")
            .exchange()
            .expectStatus().isOk();

        assertThat(aplicacao.proxima().caminho()).isEqualTo("/graphiql?path=/graphql");
    }

    @Test
    void caminhoForaDasRotasNaoChegaNaAplicacao() {
        http.get().uri("/admin/usuarios")
            .exchange()
            .expectStatus().isNotFound();

        assertThat(aplicacao.nadaRecebido()).isTrue();
    }

    @Test
    void healthDoProprioGatewayNaoPassaPelaAplicacao() {
        http.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.status").isEqualTo("UP");

        assertThat(aplicacao.nadaRecebido()).isTrue();
    }
}
