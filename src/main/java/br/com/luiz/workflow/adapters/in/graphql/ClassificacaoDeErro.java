package br.com.luiz.workflow.adapters.in.graphql;

import graphql.ErrorClassification;

/**
 * Classificações de erro próprias desta API, além das do Spring
 * (BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, INTERNAL_ERROR).
 * Saem na resposta em errors[].extensions.classification.
 */
public enum ClassificacaoDeErro implements ErrorClassification {

    /** Outra operação alterou o recurso depois da leitura: releia e decida de novo. */
    CONFLITO
}