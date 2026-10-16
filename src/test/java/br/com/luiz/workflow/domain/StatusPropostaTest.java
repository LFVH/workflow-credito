package br.com.luiz.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StatusPropostaTest {

    @Test
    void rascunhoPodeIrParaEmAnalise() {
        assertThat(StatusProposta.RASCUNHO.podeIrPara(StatusProposta.EM_ANALISE)).isTrue();
    }

    @Test
    void rascunhoNaoPodeIrDiretoParaAprovada() {
        assertThat(StatusProposta.RASCUNHO.podeIrPara(StatusProposta.APROVADA)).isFalse();
    }
    @Test
    void rascunhoNaoPodeIrDiretoParaPendenteDocumentos() {
        assertThat(StatusProposta.RASCUNHO.podeIrPara(StatusProposta.PENDENTE_DOCUMENTOS)).isFalse();
    }
    @Test
    void emAnalisePodeIrParaPendenteDocumentos() {
        assertThat(StatusProposta.EM_ANALISE.podeIrPara(StatusProposta.PENDENTE_DOCUMENTOS)).isTrue();
    }

    @Test
    void pendenteDocumentosNaoPodeIrDiretoParaAprovada() {
        assertThat(StatusProposta.PENDENTE_DOCUMENTOS.podeIrPara(StatusProposta.APROVADA)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = StatusProposta.class, names = {"APROVADA", "REPROVADA", "CANCELADA"})
    void estadosFinaisNaoTemSaida(StatusProposta status) {
        assertThat(status.ehFinal()).isTrue();
    }
}