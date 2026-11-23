package br.com.luiz.workflow.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.luiz.workflow.application.PropostaDesatualizadaException;
import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusProposta;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
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

    // ===== paginação keyset =====
    // O @DataJpaTest roda cada teste numa transação só: todo INSERT daqui recebe o MESMO
    // criada_em (DEFAULT now() = início da transação). O empate acontece de graça.

    @Test
    void primeiraPaginaOrdenaPorCriadaEmEDepoisPorId() {
        salvarNova(id(1));
        salvarNova(id(2));
        salvarNova(id(3));
        criadaUmaHoraDepois(id(1)); // a de menor id passa a ser a mais recente
        em.clear();

        // criada_em decide primeiro; no empate (2 e 3), decide o id.
        assertThat(ids(repository.listar(null, 10))).containsExactly(id(2), id(3), id(1));
        assertThat(ids(repository.listar(null, 2))).containsExactly(id(2), id(3));
    }

    @Test
    void empateNoCriadaEmEhDesempatadoPeloIdSemPularNemRepetir() {
        for (int n : List.of(5, 3, 1, 4, 2)) { // fora de ordem de propósito
            salvarNova(id(n));
        }
        em.clear();

        Object datasDistintas = em.createNativeQuery("SELECT count(DISTINCT criada_em) FROM proposta")
            .getSingleResult();
        assertThat(((Number) datasDistintas).intValue())
            .as("as cinco nasceram na mesma transação, logo com o mesmo now()")
            .isEqualTo(1);

        List<Proposta> pagina1 = repository.listar(null, 2);
        List<Proposta> pagina2 = repository.listar(pagina1.getLast().id(), 2);
        List<Proposta> pagina3 = repository.listar(pagina2.getLast().id(), 2);
        List<Proposta> pagina4 = repository.listar(pagina3.getLast().id(), 2);

        assertThat(ids(pagina1)).containsExactly(id(1), id(2));
        assertThat(ids(pagina2)).containsExactly(id(3), id(4));
        assertThat(ids(pagina3)).containsExactly(id(5));
        assertThat(pagina4).isEmpty();
    }

    @Test
    void cursorDesconhecidoDevolveListaVazia() {
        salvarNova(id(1));
        salvarNova(id(2));
        em.clear();

        // A subconsulta pelo cursor não acha linha => a comparação vira NULL => nenhuma linha.
        assertThat(repository.listar(UUID.randomUUID(), 10)).isEmpty();
    }

    // ===== apoio =====

    private static Proposta emAnalise() {
        Proposta p = Proposta.nova(new BigDecimal("10000.00"), 24);
        p.enviarParaAnalise();
        return p;
    }

    /**
     * Grava uma proposta nova com o id escolhido, para a ordem do desempate ser previsível.
     * Versão null = nova: o Spring Data faz persist (INSERT), e o banco preenche criada_em.
     */
    private void salvarNova(UUID id) {
        repository.salvar(Proposta.reconstituir(id, new BigDecimal("1000.00"), 12,
            StatusProposta.RASCUNHO, null, null));
    }

    /** Simula uma proposta criada depois: empurra o criada_em uma hora para frente. */
    private void criadaUmaHoraDepois(UUID id) {
        em.createNativeQuery("UPDATE proposta SET criada_em = criada_em + interval '1 hour' WHERE id = :id")
            .setParameter("id", id)
            .executeUpdate();
    }

    /** id(1) = 00000000-0000-0000-0000-000000000001, e assim por diante. */
    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(n));
    }

    private static List<UUID> ids(List<Proposta> propostas) {
        return propostas.stream().map(proposta -> proposta.id()).toList();
    }
}
