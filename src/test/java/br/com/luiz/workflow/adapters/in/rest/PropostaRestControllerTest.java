package br.com.luiz.workflow.adapters.in.rest;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.luiz.workflow.application.PropostaDesatualizadaException;
import br.com.luiz.workflow.application.PropostaService;
import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusProposta;
import br.com.luiz.workflow.domain.TransicaoInvalidaException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testa só a tradução do adaptador REST: HTTP vira chamada do service; resultado e exceção viram HTTP.
 * As regras de negócio já estão cobertas pelos testes do domínio e do service.
 */
@WebMvcTest(PropostaRestController.class)
class PropostaRestControllerTest {

    private static final UUID ID = UUID.fromString("3f1c2b9e-8a4d-4c6b-9f0e-1a2b3c4d5e6f");
    private static final UUID OUTRO_ID = UUID.fromString("7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d");

    /** Por extenso de propósito: o teste é o contrato e percebe se a produção mudar a URL sem querer. */
    private static final String TIPO = "https://github.com/LFVH/workflow-credito/blob/main/docs/problemas.md#";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PropostaService service;

    private static Proposta proposta(UUID id, StatusProposta status, String motivo, long versao) {
        return Proposta.reconstituir(id, new BigDecimal("1500.50"), 12, status, motivo, versao);
    }

    // ---------- criação ----------

    @Test
    void criarDevolve201ComLocationEValorEmTexto() throws Exception {
        when(service.criar(any(), anyInt())).thenReturn(proposta(ID, StatusProposta.RASCUNHO, null, 0L));

        mvc.perform(post("/api/propostas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"valor": "1500.50", "prazoMeses": 12}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", endsWith("/api/propostas/" + ID)))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(ID.toString()))
            .andExpect(jsonPath("$.valor").isString())
            .andExpect(jsonPath("$.valor").value("1500.50"))
            .andExpect(jsonPath("$.prazoMeses").value(12))
            .andExpect(jsonPath("$.status").value("RASCUNHO"))
            .andExpect(jsonPath("$.transicoesPossiveis", contains("EM_ANALISE", "CANCELADA")))
            .andExpect(jsonPath("$.versao").value(0));

        verify(service).criar(new BigDecimal("1500.50"), 12);
    }

    @Test
    void valorNumericoChegaAoServiceComAsCasasDoTexto() throws Exception {
        when(service.criar(any(), anyInt())).thenReturn(proposta(ID, StatusProposta.RASCUNHO, null, 0L));

        mvc.perform(post("/api/propostas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"valor": 1500.50, "prazoMeses": 12}
                    """))
            .andExpect(status().isCreated());

        // BigDecimal.equals compara a escala: 1500.50 (escala 2) != 1500.5 (escala 1).
        // Se o número tivesse passado por double, chegaria 1500.5 e este verify falharia.
        verify(service).criar(new BigDecimal("1500.50"), 12);
    }

    @Test
    void valorSaiPorExtensoMesmoEmNotacaoCientifica() throws Exception {
        // 1E+3 é um BigDecimal válido (1 x 10^3) e o domínio aceita: maior que zero, sem casas demais.
        when(service.criar(any(), anyInt())).thenReturn(Proposta.nova(new BigDecimal("1E+3"), 12));

        mvc.perform(post("/api/propostas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"valor": 1E3, "prazoMeses": 12}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.valor").value("1000"));
    }

    // ---------- consulta ----------

    @Test
    void buscarDevolveAPropostaComTransicoesEmOrdemFixa() throws Exception {
        when(service.buscar(ID)).thenReturn(Optional.of(proposta(ID, StatusProposta.EM_ANALISE, null, 1L)));

        mvc.perform(get("/api/propostas/{id}", ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(ID.toString()))
            .andExpect(jsonPath("$.status").value("EM_ANALISE"))
            .andExpect(jsonPath("$.transicoesPossiveis",
                contains("PENDENTE_DOCUMENTOS", "APROVADA", "REPROVADA", "CANCELADA")))
            .andExpect(jsonPath("$.versao").value(1));
    }

    @Test
    void buscarInexistenteDevolve404ComProblemDetail() throws Exception {
        when(service.buscar(ID)).thenReturn(Optional.empty());

        mvc.perform(get("/api/propostas/{id}", ID))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value(TIPO + "proposta-nao-encontrada"))
            .andExpect(jsonPath("$.title").value("Proposta não encontrada"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("Proposta não encontrada: " + ID))
            .andExpect(jsonPath("$.instance").value("/api/propostas/" + ID));
    }

    @Test
    void idMalformadoDevolve400SemChamarOService() throws Exception {
        mvc.perform(get("/api/propostas/{id}", "nao-e-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(service);
    }

    // ---------- listagem ----------

    @Test
    void listarSemParametrosDeixaOServiceAplicarOPadrao() throws Exception {
        when(service.listar(null, null)).thenReturn(List.of(
            proposta(ID, StatusProposta.RASCUNHO, null, 0L),
            proposta(OUTRO_ID, StatusProposta.CANCELADA, "Cliente desistiu", 1L)));

        mvc.perform(get("/api/propostas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(ID.toString()))
            .andExpect(jsonPath("$[1].id").value(OUTRO_ID.toString()))
            .andExpect(jsonPath("$[1].valor").value("1500.50"))
            .andExpect(jsonPath("$[1].motivo").value("Cliente desistiu"));

        // O padrão de 20 é regra do service (item 05): o controller não inventa limite.
        verify(service).listar(null, null);
    }

    @Test
    void listarRepassaCursorELimiteDaQueryString() throws Exception {
        when(service.listar(any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/propostas")
                .param("depoisDe", ID.toString())
                .param("limite", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));

        verify(service).listar(ID, 10);
    }

    @Test
    void limiteForaDaFaixaDevolve422() throws Exception {
        when(service.listar(null, 500))
            .thenThrow(new IllegalArgumentException("Limite deve estar entre 1 e 100"));

        mvc.perform(get("/api/propostas").param("limite", "500"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value(TIPO + "dados-invalidos"))
            .andExpect(jsonPath("$.detail").value("Limite deve estar entre 1 e 100"));
    }
}
