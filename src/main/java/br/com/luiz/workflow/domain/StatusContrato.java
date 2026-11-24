package br.com.luiz.workflow.domain;

import java.util.Set;

public enum StatusContrato {
    GERADO,
    ASSINADO,
    ATIVO,
    LIQUIDADO,
    CANCELADO;

    public Set<StatusContrato> destinosPermitidos() {
        return switch (this) {
            case GERADO -> Set.of(ASSINADO, CANCELADO);
            case ASSINADO -> Set.of(ATIVO, CANCELADO);
            // Dinheiro já liberado: o contrato não "cancela", ele se encerra pela liquidação.
            case ATIVO -> Set.of(LIQUIDADO);
            case LIQUIDADO, CANCELADO -> Set.of();
        };
    }

    public boolean podeIrPara(StatusContrato destino) {
        return destinosPermitidos().contains(destino);
    }

    public boolean ehFinal() {
        return destinosPermitidos().isEmpty();
    }
}
