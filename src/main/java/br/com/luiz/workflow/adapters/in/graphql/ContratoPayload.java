package br.com.luiz.workflow.adapters.in.graphql;

import br.com.luiz.workflow.domain.Contrato;
import br.com.luiz.workflow.domain.StatusContrato;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record ContratoPayload(
        UUID id,
        UUID propostaId,
        BigDecimal valor,
        int prazoMeses,
        StatusContrato status,
        String motivo,
        Set<StatusContrato> transicoesPossiveis) {

    static ContratoPayload de(Contrato c) {
        return new ContratoPayload(
            c.id(), c.propostaId(), c.valor(), c.prazoMeses(),
            c.status(), c.motivo(), c.status().destinosPermitidos());
    }
}
