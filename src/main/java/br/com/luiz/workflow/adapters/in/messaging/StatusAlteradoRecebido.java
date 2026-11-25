package br.com.luiz.workflow.adapters.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * O que ESTE consumidor precisa da mensagem propostas.status-alterado ("leitor tolerante"):
 * só três campos. Campos a mais são ignorados, e o status vem como texto: um status novo
 * criado pelo produtor não derruba o consumidor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatusAlteradoRecebido(UUID eventoId, UUID propostaId, String para) {}
