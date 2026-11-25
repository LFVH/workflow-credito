package br.com.luiz.workflow.application.port.out;

import java.util.UUID;

/**
 * Inbox do consumidor: lembra quais eventos já foram processados.
 * Deve ser chamado dentro da mesma {@link Transacao} do efeito do evento: se o efeito
 * falhar, o registro também é desfeito, e a reentrega consegue processar.
 */
public interface EventosProcessados {

    /** @return true na primeira vez (registrou agora); false se o evento já tinha sido processado */
    boolean registrarSeNovo(UUID eventoId);
}
