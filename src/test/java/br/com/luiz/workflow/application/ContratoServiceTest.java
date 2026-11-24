package br.com.luiz.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.luiz.workflow.adapters.out.memory.ContratoRepositoryEmMemoria;
import br.com.luiz.workflow.adapters.out.memory.PropostaRepositoryEmMemoria;
import br.com.luiz.workflow.domain.Contrato;
import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusContrato;
import br.com.luiz.workflow.domain.TransicaoInvalidaException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContratoServiceTest {

    private final PropostaRepositoryEmMemoria propostas = new PropostaRepositoryEmMemoria();
    private final ContratoRepositoryEmMemoria contratos = new ContratoRepositoryEmMemoria();
    private final ContratoService service = new ContratoService(contratos, propostas);

    // ===== gerarPara =====

    @Test
    void gerarParaPropostaAprovadaGravaContratoGerado() {
        Proposta proposta = propostaAprovada();

        Contrato gerado = service.gerarPara(proposta.id());

        assertThat(gerado.status()).isEqualTo(StatusContrato.GERADO);
        assertThat(gerado.propostaId()).isEqualTo(proposta.id());
        assertThat(gerado.valor()).isEqualByComparingTo(proposta.valor());
        assertThat(gerado.versao()).isEqualTo(0L); // devolveu o que foi GRAVADO
        assertThat(service.buscarPorProposta(proposta.id())).isPresent();
    }

    @Test
    void gerarParaEhIdempotente() {
        UUID propostaId = propostaAprovada().id();

        Contrato primeiro = service.gerarPara(propostaId);
        Contrato repetido = service.gerarPara(propostaId); // a mesma mensagem chegou de novo

        assertThat(repetido.id()).isEqualTo(primeiro.id());
        assertThat(service.buscarPorPropostas(List.of(propostaId))).hasSize(1);
    }

    @Test
    void mensagemRepetidaDepoisQueOContratoAndouDevolveOEstadoAtual() {
        UUID propostaId = propostaAprovada().id();
        UUID contratoId = service.gerarPara(propostaId).id();
        service.assinar(contratoId);

        Contrato repetido = service.gerarPara(propostaId);

        assertThat(repetido.id()).isEqualTo(contratoId);
        assertThat(repetido.status()).isEqualTo(StatusContrato.ASSINADO); // não "volta" para GERADO
    }

    @Test
    void propostaQueNaoEstaAprovadaNaoGeraContrato() {
        Proposta emAnalise = Proposta.nova(new BigDecimal("15000.00"), 36);
        emAnalise.enviarParaAnalise();
        UUID propostaId = propostas.salvar(emAnalise).id();

        assertThatThrownBy(() -> service.gerarPara(propostaId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("EM_ANALISE");
        assertThat(service.buscarPorProposta(propostaId)).isEmpty();
    }

    @Test
    void propostaInexistenteLancaNaoEncontrada() {
        UUID propostaId = UUID.randomUUID();

        assertThatThrownBy(() -> service.gerarPara(propostaId))
            .isInstanceOf(PropostaNaoEncontradaException.class)
            .hasMessageContaining(propostaId.toString());
    }

    // ===== ciclo de vida =====

    @Test
    void cicloCompletoDevolveSempreOQueFoiGravado() {
        UUID id = service.gerarPara(propostaAprovada().id()).id(); // versão 0

        Contrato assinado = service.assinar(id);
        Contrato ativo = service.ativar(id);
        Contrato liquidado = service.liquidar(id);

        assertThat(assinado.status()).isEqualTo(StatusContrato.ASSINADO);
        assertThat(assinado.versao()).isEqualTo(1L);
        assertThat(ativo.status()).isEqualTo(StatusContrato.ATIVO);
        assertThat(ativo.versao()).isEqualTo(2L);
        assertThat(liquidado.status()).isEqualTo(StatusContrato.LIQUIDADO);
        assertThat(liquidado.versao()).isEqualTo(3L);
    }

    @Test
    void cancelaComMotivo() {
        UUID id = service.gerarPara(propostaAprovada().id()).id();

        Contrato cancelado = service.cancelar(id, "Cliente desistiu antes de assinar");

        assertThat(cancelado.status()).isEqualTo(StatusContrato.CANCELADO);
        assertThat(cancelado.motivo()).isEqualTo("Cliente desistiu antes de assinar");
    }

    @Test
    void transicaoInvalidaNaoGrava() {
        UUID id = service.gerarPara(propostaAprovada().id()).id();

        assertThatThrownBy(() -> service.ativar(id))
            .isInstanceOf(TransicaoInvalidaException.class);
        assertThat(service.buscar(id).orElseThrow().status()).isEqualTo(StatusContrato.GERADO);
    }

    @Test
    void contratoInexistenteLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.assinar(id))
            .isInstanceOf(ContratoNaoEncontradoException.class)
            .hasMessage("Contrato não encontrado: " + id);
    }

    // ===== consultas =====

    @Test
    void buscarPorPropostasDevolveSoAsQueTemContrato() {
        UUID comContrato1 = propostaAprovada().id();
        UUID comContrato2 = propostaAprovada().id();
        UUID semContrato = propostaAprovada().id();
        service.gerarPara(comContrato1);
        service.gerarPara(comContrato2);

        List<Contrato> encontrados = service.buscarPorPropostas(List.of(comContrato1, comContrato2, semContrato));

        assertThat(encontrados).extracting(Contrato::propostaId)
            .containsExactlyInAnyOrder(comContrato1, comContrato2);
    }

    @Test
    void buscarPorPropostasComColecaoVaziaDevolveListaVazia() {
        assertThat(service.buscarPorPropostas(List.of())).isEmpty();
    }

    // ===== apoio =====

    /** Proposta aprovada pelo fluxo real, gravada no fake (continua com o objeto devolvido). */
    private Proposta propostaAprovada() {
        Proposta p = Proposta.nova(new BigDecimal("15000.00"), 36);
        p.enviarParaAnalise();
        p.aprovar();
        return propostas.salvar(p);
    }
}
