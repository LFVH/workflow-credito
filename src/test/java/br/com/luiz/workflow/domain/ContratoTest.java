package br.com.luiz.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ContratoTest {

    // ===== geração =====

    @Test
    void gerarParaPropostaAprovadaCopiaAsCondicoesAprovadas() {
        Proposta proposta = propostaAprovada();

        Contrato c = Contrato.gerarPara(proposta);

        assertThat(c.id()).isNotNull().isNotEqualTo(proposta.id()); // identidade própria
        assertThat(c.propostaId()).isEqualTo(proposta.id());        // referência por id
        assertThat(c.valor()).isEqualByComparingTo("15000.00");
        assertThat(c.prazoMeses()).isEqualTo(36);
        assertThat(c.status()).isEqualTo(StatusContrato.GERADO);
        assertThat(c.motivo()).isNull();
        assertThat(c.versao()).isNull(); // ainda não foi gravado
    }

    @ParameterizedTest
    @EnumSource(value = StatusProposta.class, names = "APROVADA", mode = EnumSource.Mode.EXCLUDE)
    void naoGeraContratoParaPropostaQueNaoEstaAprovada(StatusProposta status) {
        // reconstituir não valida: é o jeito curto de ter uma proposta em qualquer status.
        Proposta proposta = Proposta.reconstituir(UUID.randomUUID(), new BigDecimal("15000.00"), 36,
            status, "motivo qualquer", 0L);

        assertThatThrownBy(() -> Contrato.gerarPara(proposta))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(status.name());
    }

    // ===== ciclo de vida =====

    @Test
    void cicloCompletoAteALiquidacao() {
        Contrato c = contratoGerado();

        c.assinar();
        c.ativar();
        c.liquidar();

        assertThat(c.status()).isEqualTo(StatusContrato.LIQUIDADO);
    }

    @Test
    void naoAtivaSemAssinar() {
        Contrato c = contratoGerado();

        assertThatThrownBy(c::ativar)
            .isInstanceOf(TransicaoInvalidaException.class)
            .hasMessage("Transição inválida: GERADO -> ATIVO");
        assertThat(c.status()).isEqualTo(StatusContrato.GERADO);
    }

    @Test
    void contratoAtivoNaoPodeSerCancelado() {
        Contrato c = contratoGerado();
        c.assinar();
        c.ativar();

        assertThatThrownBy(() -> c.cancelar("Cliente desistiu"))
            .isInstanceOf(TransicaoInvalidaException.class);
        assertThat(c.status()).isEqualTo(StatusContrato.ATIVO);
        assertThat(c.motivo()).isNull();
    }

    @Test
    void contratoAssinadoPodeSerCanceladoComMotivo() {
        Contrato c = contratoGerado();
        c.assinar();

        c.cancelar("Cliente desistiu antes da liberação");

        assertThat(c.status()).isEqualTo(StatusContrato.CANCELADO);
        assertThat(c.motivo()).isEqualTo("Cliente desistiu antes da liberação");
    }

    @Test
    void cancelarExigeMotivo() {
        Contrato c = contratoGerado();

        assertThatThrownBy(() -> c.cancelar("  "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.cancelar(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(c.status()).isEqualTo(StatusContrato.GERADO);
    }

    // ===== reconstituição =====

    @Test
    void reconstituirRestauraOEstadoGravado() {
        UUID id = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();

        Contrato c = Contrato.reconstituir(id, propostaId, new BigDecimal("15000.00"), 36,
            StatusContrato.CANCELADO, "Fraude detectada", 4L);

        assertThat(c.id()).isEqualTo(id);
        assertThat(c.propostaId()).isEqualTo(propostaId);
        assertThat(c.valor()).isEqualByComparingTo("15000.00");
        assertThat(c.prazoMeses()).isEqualTo(36);
        assertThat(c.status()).isEqualTo(StatusContrato.CANCELADO);
        assertThat(c.motivo()).isEqualTo("Fraude detectada");
        assertThat(c.versao()).isEqualTo(4L);
    }

    // ===== apoio =====

    /** Proposta aprovada pelo fluxo real do domínio. */
    private static Proposta propostaAprovada() {
        Proposta p = Proposta.nova(new BigDecimal("15000.00"), 36);
        p.enviarParaAnalise();
        p.aprovar();
        return p;
    }

    private static Contrato contratoGerado() {
        return Contrato.gerarPara(propostaAprovada());
    }
}
