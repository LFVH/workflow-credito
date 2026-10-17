package br.com.luiz.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.luiz.workflow.adapters.out.memory.PropostaRepositoryEmMemoria;
import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusProposta;
import br.com.luiz.workflow.domain.TransicaoInvalidaException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PropostaServiceTest {

    private final PropostaService service = new PropostaService(new PropostaRepositoryEmMemoria());

    @Test
    void criaPropostaEmRascunhoEPersiste() {
        Proposta criada = service.criar(new BigDecimal("5000"), 12);

        assertThat(criada.status()).isEqualTo(StatusProposta.RASCUNHO);
        assertThat(service.buscar(criada.id())).isPresent();
    }

    @Test
    void aprovaPropostaEmAnalise() {
        Proposta p = service.criar(new BigDecimal("5000"), 12);
        service.enviarParaAnalise(p.id());

        Proposta aprovada = service.aprovar(p.id());

        assertThat(aprovada.status()).isEqualTo(StatusProposta.APROVADA);
    }

    @Test
    void aprovarSemPassarPorAnaliseFalha() {
        Proposta p = service.criar(new BigDecimal("5000"), 12);

        assertThatThrownBy(() -> service.aprovar(p.id()))
            .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void idInexistenteLancaNaoEncontrada() {
        assertThatThrownBy(() -> service.aprovar(UUID.randomUUID()))
            .isInstanceOf(PropostaNaoEncontradaException.class);
    }

    @Test
    void listaTodasAsPropostas() {
        service.criar(new BigDecimal("1000"), 6);
        service.criar(new BigDecimal("2000"), 12);

        assertThat(service.listar()).hasSize(2);
    }
}