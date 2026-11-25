package br.com.luiz.workflow.application;

import br.com.luiz.workflow.application.port.out.Transacao;
import java.util.function.Supplier;

/**
 * Fake do port Transacao: só chama o trabalho, sem banco e sem rollback.
 * Conta as execuções e diz se há uma "transação" aberta, para os testes
 * conferirem que o service delimita a transação no lugar certo.
 */
public class TransacaoDireta implements Transacao {

    private int execucoes;
    private int abertas; // passa de 1 quando há executar dentro de executar

    @Override
    public <T> T executar(Supplier<T> trabalho) {
        execucoes++;
        abertas++;
        try {
            return trabalho.get();
        } finally {
            abertas--; // roda mesmo se o trabalho lançar exceção
        }
    }

    /** Quantas vezes executar foi chamado (inclusive aninhado). */
    public int execucoes() {
        return execucoes;
    }

    /** true enquanto algum trabalho está rodando. */
    public boolean ativa() {
        return abertas > 0;
    }
}
