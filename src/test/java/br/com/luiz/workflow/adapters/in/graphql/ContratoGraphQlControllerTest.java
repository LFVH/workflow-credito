package br.com.luiz.workflow.adapters.in.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.luiz.workflow.application.ContratoDesatualizadoException;
import br.com.luiz.workflow.application.ContratoNaoEncontradoException;
import br.com.luiz.workflow.application.ContratoService;
import br.com.luiz.workflow.application.PropostaNaoEncontradaException;
import br.com.luiz.workflow.application.PropostaService;
import br.com.luiz.workflow.domain.Contrato;
import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusContrato;
import br.com.luiz.workflow.domain.StatusProposta;
import br.com.luiz.workflow.domain.TransicaoInvalidaException;
import graphql.ErrorClassification;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Testes do adaptador GraphQL de contrato, em slice: sem banco e sem servidor HTTP.
 * Testa a TRADUÇÃO (argumentos -> service, domínio -> payload, exceção -> erro GraphQL) e,
 * nos testes de Proposta.contrato, QUANTAS vezes o service é chamado (o N+1).
 * Sobe os DOIS controllers: Proposta.contrato só é alcançável pela query propostas,
 * que é atendida pelo PropostaGraphQlController.
 */
@GraphQlTest({ContratoGraphQlController.class, PropostaGraphQlController.class})
@Import({GraphQlScalarsConfig.class, GraphQlErrorHandler.class})
class ContratoGraphQlControllerTest {

    @Autowired
    GraphQlTester graphQlTester;

    @MockitoBean
    ContratoService contratoService;

    @MockitoBean
    PropostaService propostaService;

    // ===== query contrato =====

    @Test
    void contratoEncontradoDevolveOsCamposDoPayload() {
        Contrato c = contrato(StatusContrato.GERADO);
        when(contratoService.buscar(any())).thenReturn(Optional.of(c));

        graphQlTester.document("""
                query Buscar($id: ID!) {
                  contrato(id: $id) {
                    id propostaId valor prazoMeses status motivo transicoesPossiveis
                  }
                }
                """)
            .variable("id", c.id().toString())
            .execute()
            .path("contrato.id").entity(String.class).isEqualTo(c.id().toString())
            .path("contrato.propostaId").entity(String.class).isEqualTo(c.propostaId().toString())
            .path("contrato.valor").entity(String.class).isEqualTo("15000.00")
            .path("contrato.prazoMeses").entity(Integer.class).isEqualTo(36)
            .path("contrato.status").entity(String.class).isEqualTo("GERADO")
            .path("contrato.motivo").valueIsNull()
            .path("contrato.transicoesPossiveis").entityList(String.class)
                .hasSize(2)
                .contains("ASSINADO", "CANCELADO");

        verify(contratoService).buscar(c.id());
    }

    @Test
    void contratoInexistenteDevolveNullSemErro() {
        when(contratoService.buscar(any())).thenReturn(Optional.empty());

        graphQlTester.document("""
                query { contrato(id: "00000000-0000-0000-0000-000000000000") { id } }
                """)
            .execute()
            .errors().verify()
            .path("contrato").valueIsNull();
    }

    // ===== mutations =====

    @Test
    void gerarContratoRepassaOIdDaProposta() {
        Contrato c = contrato(StatusContrato.GERADO);
        when(contratoService.gerarPara(any())).thenReturn(c);

        graphQlTester.document("""
                mutation Gerar($propostaId: ID!) {
                  gerarContrato(propostaId: $propostaId) { id status }
                }
                """)
            .variable("propostaId", c.propostaId().toString())
            .execute()
            .path("gerarContrato.id").entity(String.class).isEqualTo(c.id().toString())
            .path("gerarContrato.status").entity(String.class).isEqualTo("GERADO");

        verify(contratoService).gerarPara(c.propostaId());
    }

    @Test
    void assinarAtivarELiquidarChamamOMetodoCerto() {
        UUID id = UUID.randomUUID();
        when(contratoService.assinar(any())).thenReturn(contrato(StatusContrato.ASSINADO));
        when(contratoService.ativar(any())).thenReturn(contrato(StatusContrato.ATIVO));
        when(contratoService.liquidar(any())).thenReturn(contrato(StatusContrato.LIQUIDADO));

        // Aliases: um documento, três mutations; cada resposta sai com o nome do alias.
        graphQlTester.document("""
                mutation Ciclo($id: ID!) {
                  assinado: assinarContrato(id: $id) { status }
                  ativo: ativarContrato(id: $id) { status }
                  liquidado: liquidarContrato(id: $id) { status }
                }
                """)
            .variable("id", id.toString())
            .execute()
            .path("assinado.status").entity(String.class).isEqualTo("ASSINADO")
            .path("ativo.status").entity(String.class).isEqualTo("ATIVO")
            .path("liquidado.status").entity(String.class).isEqualTo("LIQUIDADO");

        verify(contratoService).assinar(id);
        verify(contratoService).ativar(id);
        verify(contratoService).liquidar(id);
    }

    @Test
    void cancelarContratoRepassaOMotivo() {
        UUID id = UUID.randomUUID();
        when(contratoService.cancelar(any(), any())).thenReturn(contrato(StatusContrato.CANCELADO));

        graphQlTester.document("""
                mutation Cancelar($id: ID!, $motivo: String!) {
                  cancelarContrato(id: $id, motivo: $motivo) { status motivo }
                }
                """)
            .variable("id", id.toString())
            .variable("motivo", "Cliente desistiu")
            .execute()
            .path("cancelarContrato.status").entity(String.class).isEqualTo("CANCELADO");

        verify(contratoService).cancelar(id, "Cliente desistiu");
    }

    // ===== erros =====

    @Test
    void contratoNaoEncontradoViraNotFound() {
        UUID id = UUID.randomUUID();
        when(contratoService.assinar(any())).thenThrow(new ContratoNaoEncontradoException(id));

        assinar(id).errors().satisfy(erroUnico(ErrorType.NOT_FOUND, id.toString()));
    }

    @Test
    void contratoDesatualizadoViraConflito() {
        UUID id = UUID.randomUUID();
        when(contratoService.assinar(any())).thenThrow(new ContratoDesatualizadoException(id));

        assinar(id).errors().satisfy(erroUnico(ClassificacaoDeErro.CONFLITO, "alterado por outra operação"));
    }

    @Test
    void transicaoInvalidaDoContratoViraBadRequest() {
        when(contratoService.assinar(any()))
            .thenThrow(new TransicaoInvalidaException(StatusContrato.LIQUIDADO, StatusContrato.ASSINADO));

        assinar(UUID.randomUUID()).errors()
            .satisfy(erroUnico(ErrorType.BAD_REQUEST, "Transição inválida: LIQUIDADO -> ASSINADO"));
    }

    @Test
    void gerarParaPropostaNaoAprovadaViraBadRequest() {
        when(contratoService.gerarPara(any())).thenThrow(new IllegalArgumentException(
            "Contrato só pode ser gerado para proposta APROVADA (status atual: EM_ANALISE)"));

        gerar(UUID.randomUUID()).errors().satisfy(erroUnico(ErrorType.BAD_REQUEST, "status atual: EM_ANALISE"));
    }

    @Test
    void gerarParaPropostaInexistenteViraNotFound() {
        UUID propostaId = UUID.randomUUID();
        when(contratoService.gerarPara(any())).thenThrow(new PropostaNaoEncontradaException(propostaId));

        gerar(propostaId).errors().satisfy(erroUnico(ErrorType.NOT_FOUND, propostaId.toString()));
    }

    // ===== Proposta.contrato e o N+1: entram nos passos 23 e 27 =====

    @Test
    void cadaPropostaTrazOProprioContrato() {
        Proposta comContratoGerado = propostaAprovada();
        Proposta comContratoAtivo = propostaAprovada();
        Proposta semContrato = propostaAprovada();
        when(propostaService.listar(any(), any()))
            .thenReturn(List.of(comContratoGerado, comContratoAtivo, semContrato));
        existemContratos(
            contratoDa(comContratoGerado, StatusContrato.GERADO),
            contratoDa(comContratoAtivo, StatusContrato.ATIVO));

        graphQlTester.document("""
                query { propostas { id contrato { propostaId status } } }
                """)
            .execute()
            .path("propostas[0].contrato.propostaId").entity(String.class)
                .isEqualTo(comContratoGerado.id().toString())
            .path("propostas[0].contrato.status").entity(String.class).isEqualTo("GERADO")
            .path("propostas[1].contrato.status").entity(String.class).isEqualTo("ATIVO")
            .path("propostas[2].contrato").valueIsNull();
    }

    @Test
    void contratosDaPaginaSaoBuscadosNumaChamadaSo() {
        Proposta p1 = propostaAprovada();
        Proposta p2 = propostaAprovada();
        Proposta p3 = propostaAprovada();
        when(propostaService.listar(any(), any())).thenReturn(List.of(p1, p2, p3));
        existemContratos(contratoDa(p1, StatusContrato.GERADO));

        graphQlTester.document("""
                query { propostas { id contrato { status } } }
                """)
            .execute()
            .errors().verify();

        // UMA chamada com os 3 ids (no adaptador JPA, um SELECT ... IN) e nenhuma busca unitária.
        verify(contratoService).buscarPorPropostas(argThat(ids ->
            ids.size() == 3 && ids.containsAll(List.of(p1.id(), p2.id(), p3.id()))));
        verify(contratoService, never()).buscarPorProposta(any());
    }

    // ===== apoio =====

    private GraphQlTester.Response assinar(UUID id) {
        return graphQlTester.document("""
                mutation Assinar($id: ID!) { assinarContrato(id: $id) { id } }
                """)
            .variable("id", id.toString())
            .execute();
    }

    private GraphQlTester.Response gerar(UUID propostaId) {
        return graphQlTester.document("""
                mutation Gerar($propostaId: ID!) { gerarContrato(propostaId: $propostaId) { id } }
                """)
            .variable("propostaId", propostaId.toString())
            .execute();
    }

    /**
     * Exige exatamente um erro de negócio, com a classificação e o trecho de mensagem esperados.
     * Ignora o erro de propagação de nulo que o graphql-java acrescenta quando a mutation
     * (retorno Contrato!, não nulo) fica sem valor por causa da exceção.
     */
    private static Consumer<List<ResponseError>> erroUnico(ErrorClassification tipo, String trechoDaMensagem) {
        return erros -> {
            List<ResponseError> errosDeNegocio = erros.stream()
                .filter(respostaErro -> !ehPropagacaoDeNulo(respostaErro))
                .toList();
            assertThat(errosDeNegocio).hasSize(1);
            ResponseError erro = errosDeNegocio.get(0);
            assertThat(erro.getErrorType()).isEqualTo(tipo);
            assertThat(erro.getMessage()).contains(trechoDaMensagem);
        };
    }

    /** O mesmo critério do PropostaGraphQlControllerTest. */
    private static boolean ehPropagacaoDeNulo(ResponseError erro) {
        String mensagem = erro.getMessage();
        return mensagem != null && mensagem.contains("was declared as a non null type");
    }

    /** Contrato de uma proposta qualquer, já gravado (versão 0), no status pedido. */
    private static Contrato contrato(StatusContrato status) {
        String motivo = (status == StatusContrato.CANCELADO) ? "Cliente desistiu" : null;
        return Contrato.reconstituir(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("15000.00"), 36,
            status, motivo, 0L);
    }

    /**
     * Stuba AS DUAS formas de busca (uma a uma e em lote) a partir da mesma lista de contratos.
     * Assim o teste de resultado vale tanto para o @SchemaMapping quanto para o @BatchMapping.
     */
    private void existemContratos(Contrato... contratos) {
        List<Contrato> todos = List.of(contratos);
        when(contratoService.buscarPorProposta(any())).thenAnswer(invocacao -> {
            UUID propostaId = invocacao.getArgument(0);
            return todos.stream().filter(c -> c.propostaId().equals(propostaId)).findFirst();
        });
        when(contratoService.buscarPorPropostas(anyCollection())).thenAnswer(invocacao -> {
            Collection<UUID> propostaIds = invocacao.getArgument(0);
            return todos.stream().filter(c -> propostaIds.contains(c.propostaId())).toList();
        });
    }

    /** Proposta aprovada qualquer (o mock do PropostaService é quem a "devolve"). */
    private static Proposta propostaAprovada() {
        return Proposta.reconstituir(UUID.randomUUID(), new BigDecimal("15000.00"), 36,
            StatusProposta.APROVADA, null, 0L);
    }

    private static Contrato contratoDa(Proposta proposta, StatusContrato status) {
        return Contrato.reconstituir(UUID.randomUUID(), proposta.id(), proposta.valor(), proposta.prazoMeses(),
            status, null, 0L);
    }
}
