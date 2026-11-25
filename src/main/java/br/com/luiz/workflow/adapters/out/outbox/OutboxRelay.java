package br.com.luiz.workflow.adapters.out.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Relay do outbox (polling publisher): lê eventos pendentes e os publica no Kafka.
 * Entrega PELO MENOS uma vez: se cair depois do send e antes do commit, o evento sai de novo.
 * Quem consome precisa ser idempotente.
 */
class OutboxRelay {

    static final String TOPICO = "propostas.status-alterado";
    static final String HEADER_EVENTO_ID = "eventoId";
    private static final long ESPERA_CONFIRMACAO_SEGUNDOS = 10;

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transacao;
    private final int tamanhoDoLote;

    OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
                PlatformTransactionManager transactionManager, int tamanhoDoLote) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.transacao = new TransactionTemplate(transactionManager);
        this.tamanhoDoLote = tamanhoDoLote;
    }

    /** fixedDelay: o próximo ciclo começa N ms DEPOIS do fim deste; nunca dois ao mesmo tempo nesta instância. */
    @Scheduled(fixedDelayString = "${workflow.outbox.relay.intervalo-ms:1000}")
    public void publicarPendentes() {
        Integer publicados = transacao.execute(status -> publicarLote());
        if (publicados != null && publicados > 0) {
            log.debug("Outbox: {} evento(s) publicado(s)", publicados);
        }
    }

    /** Roda dentro da transação: as travas do FOR UPDATE valem até o commit. */
    private int publicarLote() {
        List<Pendente> pendentes = jdbc.query("""
                SELECT id, agregado_id, payload
                  FROM outbox_evento
                 WHERE publicado_em IS NULL
                 ORDER BY criado_em, id
                 LIMIT ?
                   FOR UPDATE SKIP LOCKED
                """,
            (rs, numeroDaLinha) -> new Pendente(
                rs.getObject("id", UUID.class),
                rs.getObject("agregado_id", UUID.class),
                rs.getString("payload")),
            tamanhoDoLote);

        int publicados = 0;
        for (Pendente pendente : pendentes) {
            try {
                enviar(pendente);
            } catch (RuntimeException ex) {
                jdbc.update("UPDATE outbox_evento SET tentativas = tentativas + 1 WHERE id = ?", pendente.id());
                log.warn("Outbox: falha ao publicar o evento {}; nova tentativa no próximo ciclo", pendente.id(), ex);
                break; // para o lote: o próximo pode ser da mesma proposta, e a ordem importa
            }
            jdbc.update("UPDATE outbox_evento SET publicado_em = now() WHERE id = ?", pendente.id());
            publicados++;
        }
        return publicados;
    }

    /** Envia e ESPERA a confirmação do Kafka: só depois disso a linha pode ser marcada como publicada. */
    private void enviar(Pendente pendente) {
        ProducerRecord<String, String> registro =
            new ProducerRecord<>(TOPICO, pendente.agregadoId().toString(), pendente.payload());
        registro.headers().add(HEADER_EVENTO_ID, pendente.id().toString().getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(registro).get(ESPERA_CONFIRMACAO_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // devolve o pedido de parada a quem gerencia a thread
            throw new IllegalStateException("Relay interrompido ao publicar o evento " + pendente.id(), ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException(
                "Kafka não confirmou o evento " + pendente.id() + " em " + ESPERA_CONFIRMACAO_SEGUNDOS + " s", ex);
        }
    }

    /** Uma linha pendente do outbox. */
    private record Pendente(UUID id, UUID agregadoId, String payload) {}
}
