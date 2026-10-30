package br.com.luiz.workflow.adapters.out.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.luiz.workflow.application.PropostaDesatualizadaException;
import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusProposta;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contrato do fake: ele precisa se comportar como o banco no que importa para os testes
 * do service (cópias e versão), senão os testes passam aqui e a produção falha.
 */
class PropostaRepositoryEmMemoriaTest {

    private final PropostaRepositoryEmMemoria repository = new PropostaRepositoryEmMemoria();

    @Test
    void propostaNovaEhGravadaComVersaoZero() {
        Proposta salva = repository.salvar(Proposta.nova(new BigDecimal("5000"), 12));

        assertThat(salva.versao()).isEqualTo(0L);
        assertThat(repository.buscarPorId(salva.id()).orElseThrow().versao()).isEqualTo(0L);
    }

    @Test
    void cadaSalvarIncrementaAVersao() {
        Proposta salva = repository.salvar(Proposta.nova(new BigDecimal("5000"), 12));
        salva.enviarParaAnalise(); // continua com o objeto DEVOLVIDO, que tem a versão gravada

        Proposta alterada = repository.salvar(salva);

        assertThat(alterada.versao()).isEqualTo(1L);
        assertThat(alterada.status()).isEqualTo(StatusProposta.EM_ANALISE);
    }

    @Test
    void alterarACopiaLidaSemSalvarNaoMudaOQueEstaGuardado() {
        UUID id = repository.salvar(Proposta.nova(new BigDecimal("5000"), 12)).id();

        repository.buscarPorId(id).orElseThrow().enviarParaAnalise(); // alterou e NÃO salvou

        assertThat(repository.buscarPorId(id).orElseThrow().status()).isEqualTo(StatusProposta.RASCUNHO);
    }

    @Test
    void duasCopiasDaMesmaVersaoSoAPrimeiraGrava() {
        UUID id = emAnalise();
        Proposta doAnalista = repository.buscarPorId(id).orElseThrow();
        Proposta doCliente = repository.buscarPorId(id).orElseThrow();

        doAnalista.aprovar();
        repository.salvar(doAnalista);
        doCliente.cancelar("Cliente desistiu");

        assertThatThrownBy(() -> repository.salvar(doCliente))
            .isInstanceOf(PropostaDesatualizadaException.class)
            .hasMessage("Proposta foi alterada por outra operação: " + id);
        assertThat(repository.buscarPorId(id).orElseThrow().status()).isEqualTo(StatusProposta.APROVADA);
    }

    @Test
    void versaoQueNaoEstaGuardadaEhRecusada() {
        // Versão não nula diz "já fui gravada". Se não há nada guardado, alguém apagou.
        // O Hibernate 6.6 faz o mesmo: merge de entidade versionada sem linha no banco falha.
        Proposta fantasma = Proposta.reconstituir(UUID.randomUUID(), new BigDecimal("5000"), 12,
            StatusProposta.EM_ANALISE, null, 3L);

        assertThatThrownBy(() -> repository.salvar(fantasma))
            .isInstanceOf(PropostaDesatualizadaException.class);
    }

    /** Cria, envia para análise e devolve o id. Sempre continua com o objeto devolvido. */
    private UUID emAnalise() {
        Proposta salva = repository.salvar(Proposta.nova(new BigDecimal("5000"), 12));
        salva.enviarParaAnalise();
        return repository.salvar(salva).id();
    }
}