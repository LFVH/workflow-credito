package br.com.luiz.workflow.application;

import java.util.UUID;

public class PropostaNaoEncontradaException extends RuntimeException {
    public PropostaNaoEncontradaException(UUID id) {
        super("Proposta não encontrada: " + id);
    }
}