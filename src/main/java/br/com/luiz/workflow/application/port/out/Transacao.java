package br.com.luiz.workflow.application.port.out;

import java.util.function.Supplier;

/**
 * Fronteira de transação, vista pela aplicação: "isto aqui é tudo ou nada".
 * Quem implementa decide o COMO (em produção, uma transação do banco).
 * <ul>
 *   <li>Se o trabalho terminar normalmente, tudo o que ele gravou é confirmado junto.</li>
 *   <li>Se lançar uma RuntimeException, nada do que ele gravou fica, e a mesma exceção sai daqui.</li>
 *   <li>Chamada aninhada (executar dentro de executar) participa da transação de fora.</li>
 * </ul>
 */
public interface Transacao {

    <T> T executar(Supplier<T> trabalho);
}
