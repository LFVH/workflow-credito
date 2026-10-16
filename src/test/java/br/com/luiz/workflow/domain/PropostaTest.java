package br.com.luiz.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PropostaTest {

    private Proposta propostaNova() {
        return Proposta.nova(new BigDecimal("10000.00"), 24);
    }

    @Test
    void novaPropostaComecaEmRascunho() {
        assertThat(propostaNova().status()).isEqualTo(StatusProposta.RASCUNHO);
    }

    @Test
    void valorZeroOuNegativoEhRejeitado() {
        assertThatThrownBy(() -> Proposta.nova(BigDecimal.ZERO, 24))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void prazoForaDoIntervaloEhRejeitado() {
        assertThatThrownBy(() -> Proposta.nova(new BigDecimal("1000"), 121))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enviarParaAnaliseMudaStatus() {
        Proposta p = propostaNova();
        p.enviarParaAnalise();
        assertThat(p.status()).isEqualTo(StatusProposta.EM_ANALISE);
    }

    @Test
    void naoPodeAprovarDireitoDoRascunho() {
        assertThatThrownBy(() -> propostaNova().aprovar())
            .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void reprovarExigeMotivo() {
        Proposta p = propostaNova();
        p.enviarParaAnalise();
        assertThatThrownBy(() -> p.reprovar("  "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transicaoRegistraEvento() {
        Proposta p = propostaNova();
        p.enviarParaAnalise();
        assertThat(p.eventos()).hasSize(1);
        assertThat(p.eventos().get(0).para()).isEqualTo(StatusProposta.EM_ANALISE);
    }

    @Test
    void propostaAprovadaNaoPodeSerCancelada() {
        Proposta p = propostaNova();
        p.enviarParaAnalise();
        p.aprovar();
        assertThatThrownBy(p::cancelar).isInstanceOf(TransicaoInvalidaException.class);
    }
}