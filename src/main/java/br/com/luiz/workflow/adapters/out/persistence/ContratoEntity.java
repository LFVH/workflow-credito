package br.com.luiz.workflow.adapters.out.persistence;

import br.com.luiz.workflow.domain.Contrato;
import br.com.luiz.workflow.domain.StatusContrato;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "contrato")
class ContratoEntity {

    @Id
    private UUID id;

    /**
     * Referência ao agregado Proposta POR ID: uma coluna UUID comum, sem @ManyToOne.
     * Carregar um contrato nunca carrega a proposta (nem por lazy loading).
     * updatable = false: o contrato nunca troca de proposta.
     */
    @Column(name = "proposta_id", nullable = false, updatable = false)
    private UUID propostaId;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(name = "prazo_meses", nullable = false)
    private int prazoMeses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusContrato status;

    @Column(name = "motivo")
    private String motivo;

    /** Lock otimista, igual à PropostaEntity: Long (wrapper) para null = contrato novo = persist. */
    @Version
    @Column(nullable = false)
    private Long versao;

    protected ContratoEntity() {} // exigido pelo JPA

    static ContratoEntity de(Contrato c) {
        ContratoEntity e = new ContratoEntity();
        e.id = c.id();
        e.propostaId = c.propostaId();
        e.valor = c.valor();
        e.prazoMeses = c.prazoMeses();
        e.status = c.status();
        e.motivo = c.motivo();
        e.versao = c.versao();
        return e;
    }

    Contrato paraDominio() {
        return Contrato.reconstituir(id, propostaId, valor, prazoMeses, status, motivo, versao);
    }
}
