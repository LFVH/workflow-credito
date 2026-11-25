package br.com.luiz.workflow.application.port.out;

import br.com.luiz.workflow.domain.MudancaDeStatus;
import java.util.List;

/**
 * Saída dos eventos de domínio. Deve ser chamado DENTRO de {@link Transacao#executar}:
 * o adaptador de produção grava os eventos no outbox, na mesma transação da mudança,
 * e um relay os entrega ao Kafka depois do commit. Não fala com o Kafka diretamente.
 */
public interface PublicadorDeEventos {

    void publicar(List<MudancaDeStatus> eventos);
}
