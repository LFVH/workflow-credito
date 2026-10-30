package br.com.luiz.workflow.adapters.in.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.com.luiz.workflow.application.PropostaDesatualizadaException;
import br.com.luiz.workflow.application.PropostaNaoEncontradaException;
import br.com.luiz.workflow.application.PropostaService;
import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusProposta;
import br.com.luiz.workflow.domain.TransicaoInvalidaException;
import graphql.ErrorClassification;
import java.math.BigDecimal;
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

@GraphQlTest(PropostaGraphQlController.class)
@Import({GraphQlScalarsConfig.class, GraphQlErrorHandler.class})
class PropostaGraphQlControllerTest {

    @Autowired
    GraphQlTester graphQlTester;

    @MockitoBean
    PropostaService service;

    @Test
    void propostaInexistenteDevolveNullSemErro() {
        UUID id = UUID.randomUUID();
        when(service.buscar(any())).thenReturn(Optional.empty());

        graphQlTester.document("""
                query Buscar($id: ID!) {
                  proposta(id: $id) { id }
                }
                """)
            .variable("id", id.toString())
            .execute()
            .errors().verify()
            .path("proposta").valueIsNull();

        verify(service).buscar(id);
    }

    @Test
    void propostaEncontradaDevolveOsCamposDoPayload() {
        Proposta p = emAnalise();
        when(service.buscar(any())).thenReturn(Optional.of(p));

        graphQlTester.document("""
                query Buscar($id: ID!) {
                  proposta(id: $id) {
                    id valor prazoMeses status motivo transicoesPossiveis
                  }
                }
                """)
            .variable("id", p.id().toString())
            .execute()
            .path("proposta.id").entity(String.class).isEqualTo(p.id().toString())
            .path("proposta.valor").entity(String.class).isEqualTo("10000.00")
            .path("proposta.prazoMeses").entity(Integer.class).isEqualTo(24)
            .path("proposta.status").entity(String.class).isEqualTo("EM_ANALISE")
            .path("proposta.motivo").valueIsNull()
            .path("proposta.transicoesPossiveis").entityList(String.class)
                .hasSize(4)
                .contains("PENDENTE_DOCUMENTOS", "APROVADA", "REPROVADA", "CANCELADA");

        verify(service).buscar(p.id());
    }

    @Test
    void propostasDevolveAListaNaOrdemQueOServiceEntregou() {
        Proposta primeira = rascunho();
        Proposta segunda = aprovada();
        when(service.listar()).thenReturn(List.of(primeira, segunda));

        graphQlTester.document("""
                query { propostas { id status } }
                """)
            .execute()
            .path("propostas[*].id").entityList(String.class)
                .containsExactly(primeira.id().toString(), segunda.id().toString())
            .path("propostas[*].status").entityList(String.class)
                .containsExactly("RASCUNHO", "APROVADA");

        verify(service).listar();
    }

    @Test
    void criarPropostaComValorEmVariavelDeTexto() {
        when(service.criar(any(), anyInt())).thenReturn(rascunho());

        graphQlTester.document("""
                mutation Criar($valor: Decimal!, $prazo: Int!) {
                  criarProposta(valor: $valor, prazoMeses: $prazo) {
                    valor prazoMeses status transicoesPossiveis
                  }
                }
                """)
            .variable("valor", "1500.50")
            .variable("prazo", 12)
            .execute()
            .path("criarProposta.valor").entity(String.class).isEqualTo("1500.50")
            .path("criarProposta.prazoMeses").entity(Integer.class).isEqualTo(12)
            .path("criarProposta.status").entity(String.class).isEqualTo("RASCUNHO")
            .path("criarProposta.transicoesPossiveis").entityList(String.class)
                .hasSize(2)
                .contains("EM_ANALISE", "CANCELADA");

        verify(service).criar(new BigDecimal("1500.50"), 12);
        verifyNoMoreInteractions(service);
    }

    @Test
    void criarPropostaComValorLiteralEmTexto() {
        when(service.criar(any(), anyInt())).thenReturn(rascunho());

        graphQlTester.document("""
                mutation {
                  criarProposta(valor: "1500.50", prazoMeses: 12) { valor }
                }
                """)
            .execute()
            .path("criarProposta.valor").entity(String.class).isEqualTo("1500.50");

        verify(service).criar(new BigDecimal("1500.50"), 12);
    }

    @Test
    void criarPropostaComValorLiteralNumericoPreservaAsCasasDecimais() {
        when(service.criar(any(), anyInt())).thenReturn(rascunho());

        graphQlTester.document("""
                mutation {
                  criarProposta(valor: 1500.50, prazoMeses: 12) { valor }
                }
                """)
            .execute()
            .errors().verify();

        verify(service).criar(new BigDecimal("1500.50"), 12);
    }

    @Test
    void valorFloatEmVariavelEhRejeitadoPeloScalarAntesDoController() {
        graphQlTester.document("""
                mutation Criar($valor: Decimal!, $prazo: Int!) {
                  criarProposta(valor: $valor, prazoMeses: $prazo) { id }
                }
                """)
            .variable("valor", 1500.5)
            .variable("prazo", 12)
            .execute()
            .errors()
            .satisfy(erroUnico(graphql.ErrorType.ValidationError, "Envie valores decimais como texto"));

        verifyNoInteractions(service);
    }

    @Test
    void motivoAusenteEhBarradoPeloSchemaAntesDoController() {
        graphQlTester.document("""
                mutation Cancelar($id: ID!) {
                  cancelarProposta(id: $id) { id }
                }
                """)
            .variable("id", UUID.randomUUID().toString())
            .execute()
            .errors()
            .satisfy(erroUnico(graphql.ErrorType.ValidationError, "motivo"));

        verifyNoInteractions(service);
    }

    @Test
    void enviarParaAnaliseChamaOServiceComOId() {
        Proposta p = emAnalise();
        when(service.enviarParaAnalise(any())).thenReturn(p);

        graphQlTester.document("""
                mutation Enviar($id: ID!) {
                  enviarParaAnalise(id: $id) { id status }
                }
                """)
            .variable("id", p.id().toString())
            .execute()
            .path("enviarParaAnalise.id").entity(String.class).isEqualTo(p.id().toString())
            .path("enviarParaAnalise.status").entity(String.class).isEqualTo("EM_ANALISE");

        verify(service).enviarParaAnalise(p.id());
        verifyNoMoreInteractions(service);
    }

    @Test
    void pedirDocumentosChamaOServiceComOId() {
        Proposta p = pendenteDeDocumentos();
        when(service.pedirDocumentos(any())).thenReturn(p);

        graphQlTester.document("""
                mutation Pedir($id: ID!) {
                  pedirDocumentos(id: $id) { status transicoesPossiveis }
                }
                """)
            .variable("id", p.id().toString())
            .execute()
            .path("pedirDocumentos.status").entity(String.class).isEqualTo("PENDENTE_DOCUMENTOS")
            .path("pedirDocumentos.transicoesPossiveis").entityList(String.class)
                .hasSize(2)
                .contains("EM_ANALISE", "CANCELADA");

        verify(service).pedirDocumentos(p.id());
        verifyNoMoreInteractions(service);
    }

    @Test
    void aprovarPropostaChamaAprovarEDevolveEstadoFinalSemTransicoes() {
        Proposta p = aprovada();
        when(service.aprovar(any())).thenReturn(p);

        graphQlTester.document("""
                mutation Aprovar($id: ID!) {
                  aprovarProposta(id: $id) { status transicoesPossiveis }
                }
                """)
            .variable("id", p.id().toString())
            .execute()
            .path("aprovarProposta.status").entity(String.class).isEqualTo("APROVADA")
            .path("aprovarProposta.transicoesPossiveis").entityList(String.class).hasSize(0);

        verify(service).aprovar(p.id());
        verifyNoMoreInteractions(service);
    }

    @Test
    void reprovarPropostaRepassaIdEMotivoAoService() {
        Proposta p = reprovada();
        when(service.reprovar(eq(p.id()), anyString())).thenReturn(p);

        graphQlTester.document("""
                mutation Reprovar($id: ID!, $motivo: String!) {
                  reprovarProposta(id: $id, motivo: $motivo) { status motivo }
                }
                """)
            .variable("id", p.id().toString())
            .variable("motivo", "Renda insuficiente")
            .execute()
            .path("reprovarProposta.status").entity(String.class).isEqualTo("REPROVADA")
            .path("reprovarProposta.motivo").entity(String.class).isEqualTo("Renda insuficiente");

        verify(service).reprovar(p.id(), "Renda insuficiente");
        verifyNoMoreInteractions(service);
    }

    @Test
    void cancelarPropostaRepassaIdEMotivoAoService() {
        Proposta p = cancelada();
        when(service.cancelar(any(), anyString())).thenReturn(p);

        graphQlTester.document("""
                mutation Cancelar($id: ID!, $motivo: String!) {
                  cancelarProposta(id: $id, motivo: $motivo) { status motivo }
                }
                """)
            .variable("id", p.id().toString())
            .variable("motivo", "Cliente desistiu")
            .execute()
            .path("cancelarProposta.status").entity(String.class).isEqualTo("CANCELADA")
            .path("cancelarProposta.motivo").entity(String.class).isEqualTo("Cliente desistiu");

        verify(service).cancelar(p.id(), "Cliente desistiu");
        verifyNoMoreInteractions(service);
    }

    @Test
    void transicaoInvalidaViraBadRequest() {
        when(service.aprovar(any()))
            .thenThrow(new TransicaoInvalidaException(StatusProposta.RASCUNHO, StatusProposta.APROVADA));

        graphQlTester.document("""
                mutation Aprovar($id: ID!) {
                  aprovarProposta(id: $id) { id }
                }
                """)
            .variable("id", UUID.randomUUID().toString())
            .execute()
            .errors()
            .satisfy(erroUnico(ErrorType.BAD_REQUEST, "RASCUNHO -> APROVADA"));
    }

    @Test
    void argumentoInvalidoViraBadRequest() {
        when(service.criar(any(), anyInt()))
            .thenThrow(new IllegalArgumentException("Prazo deve estar entre 1 e 120 meses"));

        graphQlTester.document("""
                mutation Criar($valor: Decimal!, $prazo: Int!) {
                  criarProposta(valor: $valor, prazoMeses: $prazo) { id }
                }
                """)
            .variable("valor", "1000.00")
            .variable("prazo", 121)
            .execute()
            .errors()
            .satisfy(erroUnico(ErrorType.BAD_REQUEST, "Prazo deve estar entre 1 e 120 meses"));

        verify(service).criar(new BigDecimal("1000.00"), 121);
    }

    @Test
    void propostaNaoEncontradaViraNotFound() {
        UUID id = UUID.randomUUID();
        when(service.enviarParaAnalise(any())).thenThrow(new PropostaNaoEncontradaException(id));

        graphQlTester.document("""
                mutation Enviar($id: ID!) {
                  enviarParaAnalise(id: $id) { id }
                }
                """)
            .variable("id", id.toString())
            .execute()
            .errors()
            .satisfy(erroUnico(ErrorType.NOT_FOUND, id.toString()));
    }

    @Test
    void propostaDesatualizadaViraConflito() {
        UUID id = UUID.randomUUID();
        when(service.aprovar(any())).thenThrow(new PropostaDesatualizadaException(id));

        graphQlTester.document("""
                mutation Aprovar($id: ID!) {
                  aprovarProposta(id: $id) { id }
                }
                """)
            .variable("id", id.toString())
            .execute()
            .errors()
            .satisfy(erroUnico(ClassificacaoDeErro.CONFLITO, "alterada por outra operação"));

        verify(service).aprovar(id);
    }

    private static Consumer<List<ResponseError>> erroUnico(ErrorClassification tipo, String trechoDaMensagem) {
        return erros -> {
            List<ResponseError> errosDeNegocio = erros.stream()
                .filter(respostaErro -> !respostaErro.getMessage().contains("was declared as a non null type"))
                .toList();
            assertThat(errosDeNegocio).hasSize(1);

            ResponseError erro = errosDeNegocio.get(0);
            assertThat(erro.getErrorType()).isEqualTo(tipo);
            assertThat(erro.getMessage()).contains(trechoDaMensagem);

            long errosDePropagacao = erros.stream()
                .filter(respostaErro -> respostaErro.getMessage().contains("was declared as a non null type"))
                .count();
            assertThat(errosDePropagacao).isEqualTo(erros.size() - 1L);
        };
    }

    private static Proposta rascunho() {
        return Proposta.nova(new BigDecimal("1500.50"), 12);
    }

    private static Proposta emAnalise() {
        Proposta p = Proposta.nova(new BigDecimal("10000.00"), 24);
        p.enviarParaAnalise();
        return p;
    }

    private static Proposta pendenteDeDocumentos() {
        Proposta p = emAnalise();
        p.pedirDocumentos();
        return p;
    }

    private static Proposta aprovada() {
        Proposta p = emAnalise();
        p.aprovar();
        return p;
    }

    private static Proposta reprovada() {
        Proposta p = emAnalise();
        p.reprovar("Renda insuficiente");
        return p;
    }

    private static Proposta cancelada() {
        Proposta p = rascunho();
        p.cancelar("Cliente desistiu");
        return p;
    }
}
