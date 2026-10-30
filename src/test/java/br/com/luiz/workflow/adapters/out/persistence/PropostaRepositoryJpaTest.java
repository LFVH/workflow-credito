package br.com.luiz.workflow.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.luiz.workflow.application.PropostaDesatualizadaException;
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
    void persisteMotivoDoCancelamento() {
        Proposta p = Proposta.nova(new BigDecimal("1500.50"), 12);
        p.cancelar("Cliente desistiu");
        repository.salvar(p);
        em.flush();
        em.clear();

        Proposta recuperada = repository.buscarPorId(p.id()).orElseThrow();

        assertThat(recuperada.status()).isEqualTo(StatusProposta.CANCELADA);
        assertThat(recuperada.motivo()).isEqualTo("Cliente desistiu");
        assertThat(recuperada.valor()).isEqualByComparingTo("1500.50");
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

    // ===== lock otimista (@Version) =====

    @Test
    void propostaNovaGravaVersaoZeroECadaAlteracaoIncrementa() {
        Proposta salva = repository.salvar(Proposta.nova(new BigDecimal("5000.00"), 12));
        assertThat(salva.versao()).isEqualTo(0L); // persist: o Hibernate "semeia" a versão com 0

        salva.enviarParaAnalise();
        Proposta alterada = repository.salvar(salva);
        assertThat(alterada.versao()).isEqualTo(1L); // só é 1 aqui porque o salvar faz flush

        em.clear();
        assertThat(repository.buscarPorId(salva.id()).orElseThrow().versao()).isEqualTo(1L);
    }

    @Test
    void segundaCopiaDaMesmaVersaoEhRecusada() {
        UUID id = repository.salvar(emAnalise()).id();
        em.clear();

        Proposta doAnalista = repository.buscarPorId(id).orElseThrow();
        Proposta doCliente = repository.buscarPorId(id).orElseThrow();
        assertThat(doCliente.versao()).isEqualTo(doAnalista.versao());

        doAnalista.aprovar();
        repository.salvar(doAnalista);

        // Barrado já no merge: a cópia diz versão 0, a entidade gerenciada já está na 1.
        doCliente.cancelar("Cliente desistiu");
        assertThatThrownBy(() -> repository.salvar(doCliente))
            .isInstanceOf(PropostaDesatualizadaException.class)
            .hasMessageContaining(id.toString());
        // Não consulte o banco depois da exceção: a sessão fica marcada para rollback.
    }

    @Test
    void updateQueNaoEncontraAVersaoLidaEhRecusado() {
        UUID id = repository.salvar(emAnalise()).id();
        Proposta doAnalista = repository.buscarPorId(id).orElseThrow(); // versão 0

        // Outra transação (outra instância da aplicação) grava antes: no banco a versão vira 1.
        // O UPDATE nativo não atualiza a entidade que o EntityManager já tem em memória (versão 0).
        em.createNativeQuery("""
                UPDATE proposta
                   SET status = 'CANCELADA', motivo = 'Cliente desistiu', versao = versao + 1
                 WHERE id = :id
                """)
            .setParameter("id", id)
            .executeUpdate();

        doAnalista.aprovar();

        // O merge não percebe nada (memória diz 0 e a cópia diz 0); quem barra é o
        // UPDATE ... WHERE id = ? AND versao = 0, que afeta 0 linhas.
        assertThatThrownBy(() -> repository.salvar(doAnalista))
            .isInstanceOf(PropostaDesatualizadaException.class);
    }

    private static Proposta emAnalise() {
        Proposta p = Proposta.nova(new BigDecimal("10000.00"), 24);
        p.enviarParaAnalise();
        return p;
    }
}