package br.com.luiz.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransicaoInvalidaExceptionTest {

    /** Uma máquina de estados qualquer, que não é a da proposta nem a do contrato. */
    private enum Semaforo { VERDE, AMARELO }

    @Test
    void mensagemTrazOrigemEDestinoDaProposta() {
        // Rede de segurança do refactor: a mensagem não pode mudar.
        assertThat(new TransicaoInvalidaException(StatusProposta.RASCUNHO, StatusProposta.APROVADA))
            .hasMessage("Transição inválida: RASCUNHO -> APROVADA");
    }

    @Test
    void serveParaQualquerMaquinaDeEstados() {
        assertThat(new TransicaoInvalidaException(Semaforo.VERDE, Semaforo.AMARELO))
            .hasMessage("Transição inválida: VERDE -> AMARELO");
    }
}
