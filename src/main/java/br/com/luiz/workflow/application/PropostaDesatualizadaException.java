package br.com.luiz.workflow.application;

import java.util.UUID;

/**
 * A proposta foi alterada por outra operação depois de lida (lock otimista).
 * Não é erro de regra nem de entrada: quem chamou deve reler a proposta e decidir de novo.
 */
public class PropostaDesatualizadaException extends RuntimeException {
    public PropostaDesatualizadaException(UUID id) {
        super("Proposta foi alterada por outra operação: " + id);
    }
}