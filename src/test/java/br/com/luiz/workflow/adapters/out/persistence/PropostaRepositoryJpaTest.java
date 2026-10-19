package br.com.luiz.workflow.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusProposta;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(PropostaRepositoryJpa.class)
class PropostaRepositoryJpaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired PropostaRepositoryJpa repository;
    @Autowired EntityManager em;

    @Test
    void salvaERecuperaProposta() {
        Proposta p = Proposta.nova(new BigDecimal("10000.00"), 24);
        p.enviarParaAnalise();
        repository.salvar(p);
        em.flush();
        em.clear(); // força a próxima leitura a ir de fato ao banco

        Proposta recuperada = repository.buscarPorId(p.id()).orElseThrow();

        assertThat(recuperada.status()).isEqualTo(StatusProposta.EM_ANALISE);
        assertThat(recuperada.valor()).isEqualByComparingTo("10000.00");
        assertThat(recuperada.prazoMeses()).isEqualTo(24);
    }

    @Test
    void idInexistenteRetornaVazio() {
        assertThat(repository.buscarPorId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void listaNaOrdemDeCriacao() {
        repository.salvar(Proposta.nova(new BigDecimal("1000"), 6));
        repository.salvar(Proposta.nova(new BigDecimal("2000"), 12));
        em.flush();
        em.clear();

        assertThat(repository.listar()).hasSize(2);
    }
}