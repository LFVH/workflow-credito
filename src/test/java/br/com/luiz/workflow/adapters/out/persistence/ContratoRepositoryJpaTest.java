package br.com.luiz.workflow.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.luiz.workflow.application.ContratoDesatualizadoException;
import br.com.luiz.workflow.domain.Contrato;
import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusContrato;
import br.com.luiz.workflow.domain.StatusProposta;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({ContratoRepositoryJpa.class, PropostaRepositoryJpa.class})
class ContratoRepositoryJpaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ContratoRepositoryJpa repository;
    @Autowired PropostaRepositoryJpa propostas;
    @Autowired EntityManager em;

    @Test
    void salvaERecuperaPorIdEPorProposta() {
        Contrato salvo = repository.salvar(Contrato.gerarPara(propostaAprovada()));
        em.clear(); // força as próximas leituras a irem de fato ao banco

        Contrato porId = repository.buscarPorId(salvo.id()).orElseThrow();
        Contrato porProposta = repository.buscarPorPropostaId(salvo.propostaId()).orElseThrow();

        assertThat(porId.status()).isEqualTo(StatusContrato.GERADO);
        assertThat(porId.valor()).isEqualByComparingTo("15000.00");
        assertThat(porId.prazoMeses()).isEqualTo(36);
        assertThat(porId.versao()).isEqualTo(0L);
        assertThat(porProposta.id()).isEqualTo(salvo.id());
    }

    @Test
    void buscarPorVariasPropostasFazUmComandoSqlSo() {
        Proposta p1 = propostaAprovada();
        Proposta p2 = propostaAprovada();
        Proposta semContrato = propostaAprovada();
        repository.salvar(Contrato.gerarPara(p1));
        repository.salvar(Contrato.gerarPara(p2));
        em.clear();

        Statistics estatisticas = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear(); // zera a contagem: só o que vem depois interessa

        List<Contrato> encontrados = repository.buscarPorPropostaIds(List.of(p1.id(), p2.id(), semContrato.id()));

        assertThat(encontrados).extracting(Contrato::propostaId)
            .containsExactlyInAnyOrder(p1.id(), p2.id());
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(1); // 1 SELECT ... IN, e não 3
    }

    @Test
    void segundaCopiaDaMesmaVersaoEhRecusada() {
        UUID id = repository.salvar(Contrato.gerarPara(propostaAprovada())).id();
        em.clear();

        Contrato daMesa = repository.buscarPorId(id).orElseThrow();
        Contrato doBackoffice = repository.buscarPorId(id).orElseThrow();

        daMesa.assinar();
        repository.salvar(daMesa);

        doBackoffice.cancelar("Documentação divergente");
        assertThatThrownBy(() -> repository.salvar(doBackoffice))
            .isInstanceOf(ContratoDesatualizadoException.class)
            .hasMessageContaining(id.toString());
        // Não consulte o banco depois da exceção: a transação fica marcada para rollback.
    }

    @Test
    void segundoContratoParaAMesmaPropostaViolaOUnique() {
        Proposta proposta = propostaAprovada();
        repository.salvar(Contrato.gerarPara(proposta));

        assertThatThrownBy(() -> repository.salvar(Contrato.gerarPara(proposta)))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uq_contrato_proposta");
    }

    @Test
    void contratoDePropostaInexistenteViolaAChaveEstrangeira() {
        // Aprovada, mas nunca gravada: o domínio aceita, o banco não.
        Proposta fantasma = Proposta.reconstituir(UUID.randomUUID(), new BigDecimal("15000.00"), 36,
            StatusProposta.APROVADA, null, 0L);

        assertThatThrownBy(() -> repository.salvar(Contrato.gerarPara(fantasma)))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("fk_contrato_proposta");
    }

    /** Proposta aprovada e gravada de verdade (a FK exige que ela exista). */
    private Proposta propostaAprovada() {
        Proposta p = Proposta.nova(new BigDecimal("15000.00"), 36);
        p.enviarParaAnalise();
        p.aprovar();
        return propostas.salvar(p);
    }
}
