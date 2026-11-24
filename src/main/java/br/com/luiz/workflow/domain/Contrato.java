package br.com.luiz.workflow.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Contrato de crédito: nasce de uma proposta APROVADA e tem ciclo de vida próprio.
 * É um agregado separado da Proposta e a referencia só pelo id (propostaId):
 * mudar um contrato nunca carrega nem altera a proposta.
 */
public class Contrato {

    private final UUID id;
    private final UUID propostaId;
    private final BigDecimal valor;
    private final int prazoMeses;
    private StatusContrato status;
    private String motivo;
    private final Long versao;

    private Contrato(UUID id, UUID propostaId, BigDecimal valor, int prazoMeses,
                     StatusContrato status, String motivo, Long versao) {
        this.id = id;
        this.propostaId = propostaId;
        this.valor = valor;
        this.prazoMeses = prazoMeses;
        this.status = status;
        this.motivo = motivo;
        this.versao = versao;
    }

    /**
     * Gera o contrato de uma proposta aprovada, copiando as condições aprovadas.
     * A cópia é intencional: o que o cliente assina não muda se outro registro mudar depois.
     */
    public static Contrato gerarPara(Proposta proposta) {
        if (proposta.status() != StatusProposta.APROVADA) {
            throw new IllegalArgumentException(
                "Contrato só pode ser gerado para proposta APROVADA (status atual: " + proposta.status() + ")");
        }
        // versão null: ainda não foi gravado
        return new Contrato(UUID.randomUUID(), proposta.id(), proposta.valor(), proposta.prazoMeses(),
            StatusContrato.GERADO, null, null);
    }

    /** Recria um contrato já gravado, sem validar. A versão é a lida do armazenamento. */
    public static Contrato reconstituir(UUID id, UUID propostaId, BigDecimal valor, int prazoMeses,
                                        StatusContrato status, String motivo, Long versao) {
        return new Contrato(id, propostaId, valor, prazoMeses, status, motivo, versao);
    }

    public void assinar()  { mudarPara(StatusContrato.ASSINADO); }
    public void ativar()   { mudarPara(StatusContrato.ATIVO); }
    public void liquidar() { mudarPara(StatusContrato.LIQUIDADO); }

    public void cancelar(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Motivo é obrigatório para cancelar o contrato");
        }
        mudarPara(StatusContrato.CANCELADO);
        this.motivo = motivo;
    }

    private void mudarPara(StatusContrato destino) {
        if (!status.podeIrPara(destino)) {
            throw new TransicaoInvalidaException(status, destino);
        }
        this.status = destino;
    }

    public UUID id()                { return id; }
    public UUID propostaId()        { return propostaId; }
    public BigDecimal valor()       { return valor; }
    public int prazoMeses()         { return prazoMeses; }
    public StatusContrato status()  { return status; }
    public String motivo()          { return motivo; }
    public Long versao()            { return versao; }
}
