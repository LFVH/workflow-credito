// TransicaoInvalidaException.java
package br.com.luiz.workflow.domain;

public class TransicaoInvalidaException extends RuntimeException {
    public TransicaoInvalidaException(StatusProposta de, StatusProposta para) {
        super("Transição inválida: " + de + " -> " + para);
    }
}