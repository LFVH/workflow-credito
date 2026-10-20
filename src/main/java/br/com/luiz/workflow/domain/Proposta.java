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
    private String motivo;
    private final List<MudancaDeStatus> eventos = new ArrayList<>();

    private Proposta(UUID id, BigDecimal valor, int prazoMeses, StatusProposta status, String motivo) {
        this.id = id;
        this.valor = valor;
        this.prazoMeses = prazoMeses;
        this.status = status;
        this.motivo = motivo;
    }

    public static Proposta reconstituir(UUID id, BigDecimal valor, int prazoMeses,
                                        StatusProposta status, String motivo) {
        return new Proposta(id, valor, prazoMeses, status, motivo);
    }

    public static Proposta nova(BigDecimal valor, int prazoMeses) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Valor deve ser maior que zero");
        }
        if (valor.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Valor deve ter no máximo 2 casas decimais");
        }
        if (prazoMeses < 1 || prazoMeses > 120) {
            throw new IllegalArgumentException("Prazo deve estar entre 1 e 120 meses");
        }
        return new Proposta(UUID.randomUUID(), valor, prazoMeses, StatusProposta.RASCUNHO, null);
    }

    public void enviarParaAnalise()      { mudarPara(StatusProposta.EM_ANALISE); }
    public void pedirDocumentos()        { mudarPara(StatusProposta.PENDENTE_DOCUMENTOS); }
    public void aprovar()                { mudarPara(StatusProposta.APROVADA); }

    public void reprovar(String motivo) { mudarComMotivo(StatusProposta.REPROVADA, motivo); }
    public void cancelar(String motivo) { mudarComMotivo(StatusProposta.CANCELADA, motivo); }

    private void mudarComMotivo(StatusProposta destino, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Motivo é obrigatório para ir para " + destino);
        }
        mudarPara(destino);
        this.motivo = motivo;
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
    public String motivo()            { return motivo; }
    public List<MudancaDeStatus> eventos() { return List.copyOf(eventos); }
}