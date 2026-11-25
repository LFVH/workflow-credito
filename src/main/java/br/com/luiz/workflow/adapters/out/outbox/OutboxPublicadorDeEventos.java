package br.com.luiz.workflow.adapters.out.outbox;

import br.com.luiz.workflow.application.port.out.PublicadorDeEventos;
import br.com.luiz.workflow.domain.MudancaDeStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador do port PublicadorDeEventos: grava cada evento como uma linha em outbox_evento.
 * NÃO fala com o Kafka. Roda na transação de quem chamou: o JpaTransactionManager expõe a
 * conexão JDBC dele, e o JdbcTemplate a usa. A proposta e os eventos entram juntos no commit.
 */
@Component
class OutboxPublicadorDeEventos implements PublicadorDeEventos {

    static final String AGREGADO_PROPOSTA = "Proposta";
    static final String TIPO_STATUS_ALTERADO = "PropostaStatusAlterado";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    OutboxPublicadorDeEventos(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void publicar(List<MudancaDeStatus> eventos) {
        for (MudancaDeStatus evento : eventos) {
            UUID eventoId = UUID.randomUUID(); // id da linha = eventoId do payload = header da mensagem
            PropostaStatusAlterado mensagem = new PropostaStatusAlterado(
                eventoId, evento.propostaId(), evento.de(), evento.para(), Instant.now());

            // clock_timestamp(), e não o DEFAULT now(): now() é o início da transação e
            // empataria eventos gravados na mesma transação (a ordem viraria a do UUID).
            jdbc.update("""
                    INSERT INTO outbox_evento (id, agregado_tipo, agregado_id, tipo_evento, payload, criado_em)
                    VALUES (?, ?, ?, ?, CAST(? AS jsonb), clock_timestamp())
                    """,
                eventoId, AGREGADO_PROPOSTA, evento.propostaId(), TIPO_STATUS_ALTERADO, paraJson(mensagem));
        }
    }

    private String paraJson(PropostaStatusAlterado mensagem) {
        try {
            return json.writeValueAsString(mensagem);
        } catch (JsonProcessingException ex) {
            // checked -> unchecked: só RuntimeException desfaz a transação
            throw new IllegalStateException("Não foi possível serializar o evento " + mensagem.eventoId(), ex);
        }
    }
}
