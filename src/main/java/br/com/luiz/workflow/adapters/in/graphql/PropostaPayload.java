package br.com.luiz.workflow.adapters.in.graphql;

import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusProposta;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record PropostaPayload(
        UUID id,
        BigDecimal valor,
        int prazoMeses,
        StatusProposta status,
        String motivoReprovacao,
        Set<StatusProposta> transicoesPossiveis) {

    static PropostaPayload de(Proposta p) {
        return new PropostaPayload(
            p.id(), p.valor(), p.prazoMeses(),
            p.status(), p.motivoReprovacao(), p.status().destinosPermitidos());
    }
}