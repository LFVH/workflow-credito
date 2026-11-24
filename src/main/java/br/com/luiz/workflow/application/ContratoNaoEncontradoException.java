package br.com.luiz.workflow.application;

import java.util.UUID;

public class ContratoNaoEncontradoException extends RuntimeException {
    public ContratoNaoEncontradoException(UUID id) {
        super("Contrato não encontrado: " + id);
    }
}
