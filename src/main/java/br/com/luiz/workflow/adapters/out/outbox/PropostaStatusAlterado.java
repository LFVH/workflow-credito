package br.com.luiz.workflow.adapters.out.outbox;

import br.com.luiz.workflow.domain.StatusProposta;
import java.time.Instant;
import java.util.UUID;

/**
 * A mensagem publicada no tópico propostas.status-alterado (versão 1 do formato).
 * É um contrato público: outros sistemas leem este JSON. Acrescentar campo é seguro
 * (consumidores tolerantes ignoram o que não conhecem); renomear ou remover quebra quem lê.
 * Evento "magro": diz o que mudou; quem precisar de mais dados consulta a API.
 */
public record PropostaStatusAlterado(
        UUID eventoId,
        UUID propostaId,
        StatusProposta de,
        StatusProposta para,
        Instant ocorridoEm) {}
