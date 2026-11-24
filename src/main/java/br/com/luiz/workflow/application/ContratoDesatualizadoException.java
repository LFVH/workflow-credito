package br.com.luiz.workflow.application;

import java.util.UUID;

/**
 * O contrato foi alterado por outra operação depois de lido (lock otimista).
 * Não é erro de regra nem de entrada: quem chamou deve reler o contrato e decidir de novo.
 */
public class ContratoDesatualizadoException extends RuntimeException {
    public ContratoDesatualizadoException(UUID id) {
        super("Contrato foi alterado por outra operação: " + id);
    }
}
