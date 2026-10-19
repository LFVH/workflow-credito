package br.com.luiz.workflow.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Proposta {

    private final UUID id;
    private final BigDecimal valor;
    private final int prazoMeses;
    private StatusProposta status;
    private String motivoReprovacao;
    private final List<MudancaDeStatus> eventos = new ArrayList<>();

    private Proposta(UUID id, BigDecimal valor, int prazoMeses, StatusProposta status, String motivoReprovacao) {
        this.id = id;
        this.valor = valor;
        this.prazoMeses = prazoMeses;
        this.status = status;
        this.motivoReprovacao = motivoReprovacao;
    }

    public static Proposta reconstituir(UUID id, BigDecimal valor, int prazoMeses,
                                        StatusProposta status, String motivoReprovacao) {
        return new Proposta(id, valor, prazoMeses, status, motivoReprovacao);
    }

    public static Proposta nova(BigDecimal valor, int prazoMeses) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Valor deve ser maior que zero");
        }
        if (prazoMeses < 1 || prazoMeses > 120) {
            throw new IllegalArgumentException("Prazo deve estar entre 1 e 120 meses");
        }
        return new Proposta(UUID.randomUUID(), valor, prazoMeses, StatusProposta.RASCUNHO, null);
    }

    public void enviarParaAnalise()      { mudarPara(StatusProposta.EM_ANALISE); }
    public void pedirDocumentos()        { mudarPara(StatusProposta.PENDENTE_DOCUMENTOS); }
    public void aprovar()                { mudarPara(StatusProposta.APROVADA); }
    public void cancelar()               { mudarPara(StatusProposta.CANCELADA); }

    public void reprovar(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Motivo da reprovação é obrigatório");
        }
        mudarPara(StatusProposta.REPROVADA);
        this.motivoReprovacao = motivo;
    }

    private void mudarPara(StatusProposta destino) {
        if (!status.podeIrPara(destino)) {
            throw new TransicaoInvalidaException(status, destino);
        }
        eventos.add(new MudancaDeStatus(id, status, destino));
        this.status = destino;
    }

    public UUID id()                  { return id; }
    public BigDecimal valor()         { return valor; }
    public int prazoMeses()           { return prazoMeses; }
    public StatusProposta status()    { return status; }
    public String motivoReprovacao()  { return motivoReprovacao; }
    public List<MudancaDeStatus> eventos() { return List.copyOf(eventos); }
}