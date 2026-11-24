package br.com.luiz.workflow.domain;

/**
 * Transição de estado que a máquina de estados de um agregado não permite.
 * Recebe Enum<?> para servir a qualquer máquina (StatusProposta, StatusContrato...).
 */
public class TransicaoInvalidaException extends RuntimeException {
    public TransicaoInvalidaException(Enum<?> de, Enum<?> para) {
        super("Transição inválida: " + de + " -> " + para);
    }
}
