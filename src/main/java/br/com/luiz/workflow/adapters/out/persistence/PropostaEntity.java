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

    /**
     * Lock otimista. Todo UPDATE vira "... versao = lida + 1 WHERE id = ? AND versao = lida";
     * 0 linhas afetadas = outra operação gravou antes = exceção.
     * Long (wrapper), não long: versão null = entidade nova, e o Spring Data faz persist
     * (INSERT direto) em vez de merge (SELECT + INSERT).
     */
    @Version
    @Column(nullable = false)
    private Long versao;

    protected PropostaEntity() {} // exigido pelo JPA

    static PropostaEntity de(Proposta p) {
        PropostaEntity e = new PropostaEntity();
        e.id = p.id();
        e.valor = p.valor();
        e.prazoMeses = p.prazoMeses();
        e.status = p.status();
        e.motivo = p.motivo();
        e.versao = p.versao();
        return e;
    }

    Proposta paraDominio() {
        return Proposta.reconstituir(id, valor, prazoMeses, status, motivo, versao);
    }
}