package br.com.luiz.workflow.adapters.in.rest;

import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusProposta;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Representação REST de uma proposta. Pertence ao adaptador: o domínio não sabe que JSON existe.
 */
public record PropostaResponse(
        UUID id,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valor, // "1500.50", nunca 1500.5
        int prazoMeses,
        StatusProposta status,
        String motivo,
        List<StatusProposta> transicoesPossiveis,
        Long versao) {

    static PropostaResponse de(Proposta p) {
        return new PropostaResponse(
            p.id(),
            p.valor(),
            p.prazoMeses(),
            p.status(),
            p.motivo(),
            p.status().destinosPermitidos().stream().sorted().toList(), // ordem do enum: JSON estável
            p.versao());
    }
}
