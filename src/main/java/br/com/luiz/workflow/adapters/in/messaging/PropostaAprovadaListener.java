package br.com.luiz.workflow.adapters.in.messaging;

import br.com.luiz.workflow.application.ContratoService;
import br.com.luiz.workflow.application.port.out.EventosProcessados;
import br.com.luiz.workflow.application.port.out.Transacao;
import br.com.luiz.workflow.domain.StatusProposta;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada: traduz "a proposta mudou de status" (Kafka) em "gerar o contrato" (caso de uso).
 * O Kafka entrega PELO MENOS uma vez. Duas camadas de idempotência:
 * <ol>
 *   <li>inbox: o eventoId é registrado em evento_processado; reentrega é ignorada sem consultar nada;</li>
 *   <li>gerarPara devolve o contrato que já existe (e o UNIQUE da V5 é a última defesa).</li>
 * </ol>
 */
@Component
public class PropostaAprovadaListener {

    private static final Logger log = LoggerFactory.getLogger(PropostaAprovadaListener.class);

    private final ContratoService contratoService;
    private final Transacao transacao;
    private final EventosProcessados eventosProcessados;
    private final ObjectMapper json;

    public PropostaAprovadaListener(ContratoService contratoService, Transacao transacao,
                                    EventosProcessados eventosProcessados, ObjectMapper json) {
        this.contratoService = contratoService;
        this.transacao = transacao;
        this.eventosProcessados = eventosProcessados;
        this.json = json;
    }

    @KafkaListener(topics = "propostas.status-alterado", groupId = "workflow-contratos")
    public void aoReceber(String mensagem) throws JsonProcessingException {
        StatusAlteradoRecebido evento = json.readValue(mensagem, StatusAlteradoRecebido.class);
        if (!StatusProposta.APROVADA.name().equals(evento.para())) {
            return; // status que não interessa a este consumidor: nem ocupa o inbox
        }
        // Inbox e efeito na MESMA transação: se o gerarPara falhar, o registro some junto
        // e a reentrega consegue processar. (O gerarPara participa desta transação.)
        boolean processou = transacao.executar(() -> {
            if (!eventosProcessados.registrarSeNovo(evento.eventoId())) {
                return false; // reentrega: já processado
            }
            contratoService.gerarPara(evento.propostaId());
            return true;
        });
        if (processou) {
            log.info("Contrato garantido para a proposta {} (evento {})", evento.propostaId(), evento.eventoId());
        } else {
            log.info("Evento {} já processado; reentrega ignorada", evento.eventoId());
        }
    }
}
