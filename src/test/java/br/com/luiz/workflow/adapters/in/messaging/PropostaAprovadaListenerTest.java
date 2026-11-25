package br.com.luiz.workflow.adapters.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.luiz.workflow.application.ContratoService;
import br.com.luiz.workflow.application.TransacaoDireta;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Só a tradução do adaptador: mensagem do Kafka -> chamada do caso de uso. Sem Spring, sem Kafka. */
class PropostaAprovadaListenerTest {

    private final ContratoService contratoService = mock(ContratoService.class);
    /** Inbox de mentira: Set.add devolve true só quando o elemento é novo, o contrato de registrarSeNovo. */
    private final Set<UUID> processados = new HashSet<>();
    private final PropostaAprovadaListener listener = new PropostaAprovadaListener(
        contratoService, new TransacaoDireta(), processados::add, new ObjectMapper());

    @Test
    void propostaAprovadaGeraOContrato() throws Exception {
        UUID propostaId = UUID.randomUUID();

        listener.aoReceber(mensagem(UUID.randomUUID(), propostaId, "APROVADA"));

        verify(contratoService).gerarPara(propostaId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EM_ANALISE", "PENDENTE_DOCUMENTOS", "REPROVADA", "CANCELADA", "STATUS_DO_FUTURO"})
    void outrosStatusNaoGeramContrato(String para) throws Exception {
        listener.aoReceber(mensagem(UUID.randomUUID(), UUID.randomUUID(), para));

        verifyNoInteractions(contratoService);
        assertThat(processados).isEmpty(); // status que não interessa nem ocupa o inbox
    }

    @Test
    void campoNovoNaMensagemNaoQuebraOConsumidor() throws Exception {
        UUID propostaId = UUID.randomUUID();
        String comCamposNovos = """
            {"eventoId": "%s", "propostaId": "%s", "de": "EM_ANALISE", "para": "APROVADA",
             "ocorridoEm": "2026-10-27T12:00:00Z", "canal": "APP", "versaoDoEsquema": 2}
            """.formatted(UUID.randomUUID(), propostaId);

        listener.aoReceber(comCamposNovos);

        verify(contratoService).gerarPara(propostaId);
    }

    @Test
    void mensagemReentregueNaoChamaOServicoDeNovo() throws Exception {
        UUID eventoId = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        String mensagem = mensagem(eventoId, propostaId, "APROVADA");

        listener.aoReceber(mensagem);
        listener.aoReceber(mensagem); // o Kafka entregou de novo: mesmo eventoId

        verify(contratoService, times(1)).gerarPara(propostaId);
        assertThat(processados).containsExactly(eventoId);
    }

    /** A mensagem como o relay publica (formato do jsonb: chaves reordenadas, com espaços). */
    private static String mensagem(UUID eventoId, UUID propostaId, String para) {
        return """
            {"de": "EM_ANALISE", "para": "%s", "eventoId": "%s", "ocorridoEm": "2026-10-27T12:00:00Z", "propostaId": "%s"}
            """.formatted(para, eventoId, propostaId);
    }
}
