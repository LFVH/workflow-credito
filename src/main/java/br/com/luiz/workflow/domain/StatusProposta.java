package br.com.luiz.workflow.domain;

import java.util.Set;

public enum StatusProposta {
    RASCUNHO,
    EM_ANALISE,
    PENDENTE_DOCUMENTOS,
    APROVADA,
    REPROVADA,
    CANCELADA;

    public Set<StatusProposta> destinosPermitidos() {
        return switch (this) {
            case RASCUNHO -> Set.of(EM_ANALISE, CANCELADA);
            case EM_ANALISE -> Set.of(PENDENTE_DOCUMENTOS, APROVADA, REPROVADA, CANCELADA);
            case PENDENTE_DOCUMENTOS -> Set.of(EM_ANALISE, CANCELADA);
            case APROVADA, REPROVADA, CANCELADA -> Set.of();
        };
    }

    public boolean podeIrPara(StatusProposta destino) {
        return destinosPermitidos().contains(destino);
    }

    public boolean ehFinal() {
        return destinosPermitidos().isEmpty();
    }
}