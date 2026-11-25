package br.com.luiz.workflow.adapters.in.messaging;

import br.com.luiz.workflow.application.ContratoService;
import br.com.luiz.workflow.domain.StatusProposta;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada: traduz "a proposta mudou de status" (Kafka) em "gerar o contrato" (caso de uso).
 * O Kafka entrega PELO MENOS uma vez: a mesma mensagem pode chegar de novo, por isso este
 * consumidor só chama operações idempotentes (gerarPara devolve o contrato que já existe).
 */
@Component
public class PropostaAprovadaListener {

    private static final Logger log = LoggerFactory.getLogger(PropostaAprovadaListener.class);

    private final ContratoService contratoService;
    private final ObjectMapper json;

    public PropostaAprovadaListener(ContratoService contratoService, ObjectMapper json) {
        this.contratoService = contratoService;
        this.json = json;
    }

    @KafkaListener(topics = "propostas.status-alterado", groupId = "workflow-contratos")
    public void aoReceber(String mensagem) throws JsonProcessingException {
        StatusAlteradoRecebido evento = json.readValue(mensagem, StatusAlteradoRecebido.class);
        if (!StatusProposta.APROVADA.name().equals(evento.para())) {
            return; // este consumidor só se interessa por aprovações
        }
        contratoService.gerarPara(evento.propostaId());
        log.info("Contrato garantido para a proposta {} (evento {})", evento.propostaId(), evento.eventoId());
    }
}
