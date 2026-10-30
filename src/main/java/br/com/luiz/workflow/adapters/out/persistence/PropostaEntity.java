package br.com.luiz.workflow.adapters.out.persistence;

import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusProposta;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proposta")
class PropostaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(name = "prazo_meses", nullable = false)
    private int prazoMeses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusProposta status;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "criada_em", insertable = false, updatable = false)
    private Instant criadaEm;

    protected PropostaEntity() {} // exigido pelo JPA

    static PropostaEntity de(Proposta p) {
        PropostaEntity e = new PropostaEntity();
        e.id = p.id();
        e.valor = p.valor();
        e.prazoMeses = p.prazoMeses();
        e.status = p.status();
        e.motivo = p.motivo();
        return e;
    }

    Proposta paraDominio() {
        return Proposta.reconstituir(id, valor, prazoMeses, status, motivo, null);
    }
}