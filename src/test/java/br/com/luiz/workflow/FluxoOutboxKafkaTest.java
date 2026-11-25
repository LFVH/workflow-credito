package br.com.luiz.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import br.com.luiz.workflow.application.ContratoService;
import br.com.luiz.workflow.application.PropostaService;
import br.com.luiz.workflow.application.port.out.PropostaRepository;
import br.com.luiz.workflow.application.port.out.PublicadorDeEventos;
import br.com.luiz.workflow.application.port.out.Transacao;
import br.com.luiz.workflow.domain.Contrato;
import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusContrato;
import br.com.luiz.workflow.domain.StatusProposta;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * O fluxo inteiro da fase 5, com Postgres e Kafka de verdade (Testcontainers).
 * Pulado sem Docker (notebook); roda no GitHub Actions e no Codespaces.
 * Sem @Transactional na classe: cada caso de uso faz COMMIT de verdade, senão o relay
 * (outra thread, outra transação) nunca veria as linhas do outbox.
 */
@SpringBootTest(properties = {
    "workflow.outbox.relay.habilitado=true",
    "workflow.outbox.relay.intervalo-ms=200",
    "spring.kafka.listener.auto-startup=true"
})
@Testcontainers(disabledWithoutDocker = true)
class FluxoOutboxKafkaTest {

    private static final String TOPICO = "propostas.status-alterado";
    private static final Duration TEMPO_MAXIMO = Duration.ofSeconds(30);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Mesma versão do kafka-clients que o Spring Boot 3.5.6 gerencia (3.9.1). */
    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired PropostaService propostaService;
    @Autowired ContratoService contratoService;
    @Autowired PropostaRepository propostaRepository;
    @Autowired PublicadorDeEventos publicador;
    @Autowired Transacao transacao;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void cadaTransicaoGravaUmEventoNoOutbox() {
        UUID id = aprovada();

        assertThat(destinosNoOutbox(id)).containsExactly("EM_ANALISE", "APROVADA");
    }

    @Test
    void falhaDepoisDeGravarDesfazPropostaEOutboxJuntos() {
        UUID id = propostaService.criar(new BigDecimal("15000.00"), 36).id(); // versão 0
        propostaService.enviarParaAnalise(id);                                // versão 1

        // Grava no outbox de verdade e, DEPOIS, falha: como se a aplicação caísse antes do commit.
        PublicadorDeEventos gravaEDepoisFalha = eventos -> {
            publicador.publicar(eventos);
            throw new IllegalStateException("falha simulada depois de gravar a proposta e o outbox");
        };
        PropostaService comFalha = new PropostaService(propostaRepository, gravaEDepoisFalha, transacao);

        assertThatThrownBy(() -> comFalha.aprovar(id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("falha simulada");

        Proposta gravada = propostaService.buscar(id).orElseThrow();
        assertThat(gravada.status()).isEqualTo(StatusProposta.EM_ANALISE); // o UPDATE foi desfeito...
        assertThat(gravada.versao()).isEqualTo(1L);
        assertThat(destinosNoOutbox(id)).containsExactly("EM_ANALISE");    // ...e o INSERT do outbox também
    }

    @Test
    void relayPublicaNoTopicoComAChaveDaPropostaEOEventoIdNoCabecalho() {
        UUID id = aprovada();
        List<ConsumerRecord<String, String>> daProposta = new ArrayList<>();

        try (KafkaConsumer<String, String> consumidor = novoConsumidor()) {
            consumidor.subscribe(List.of(TOPICO));
            await().atMost(TEMPO_MAXIMO).pollInSameThread().untilAsserted(() -> {
                for (ConsumerRecord<String, String> registro : consumidor.poll(Duration.ofMillis(500))) {
                    if (id.toString().equals(registro.key())) { // o tópico tem mensagens de todos os testes
                        daProposta.add(registro);
                    }
                }
                assertThat(daProposta).hasSize(2);
            });
        }

        // Mesma chave => mesma partição => as mudanças da proposta chegam na ordem.
        assertThat(daProposta).extracting(ConsumerRecord::partition)
            .containsOnly(daProposta.get(0).partition());
        assertThat(daProposta).extracting(registro -> campo(registro, "para"))
            .containsExactly("EM_ANALISE", "APROVADA");

        // O header eventoId é o id da linha do outbox (o consumidor usa no inbox).
        List<UUID> idsNoOutbox = jdbc.queryForList(
            "SELECT id FROM outbox_evento WHERE agregado_id = ? ORDER BY criado_em, id", UUID.class, id);
        assertThat(daProposta)
            .extracting(registro -> UUID.fromString(
                new String(registro.headers().lastHeader("eventoId").value(), StandardCharsets.UTF_8)))
            .containsExactlyElementsOf(idsNoOutbox);

        // E o relay marcou as duas linhas como publicadas.
        await().atMost(TEMPO_MAXIMO).untilAsserted(() -> assertThat(pendentesNoOutbox(id)).isZero());
    }

    @Test
    void consumidorGeraOContratoDaPropostaAprovada() {
        UUID id = aprovada();

        await().atMost(TEMPO_MAXIMO).untilAsserted(() ->
            assertThat(contratoService.buscarPorProposta(id)).isPresent());

        Contrato contrato = contratoService.buscarPorProposta(id).orElseThrow();
        assertThat(contrato.status()).isEqualTo(StatusContrato.GERADO);
        assertThat(contrato.valor()).isEqualByComparingTo("15000.00");
        assertThat(contrato.prazoMeses()).isEqualTo(36);
    }

    // ===== apoio =====

    /** Cria, envia para análise e aprova pelo caso de uso real (três transações com commit). */
    private UUID aprovada() {
        UUID id = propostaService.criar(new BigDecimal("15000.00"), 36).id();
        propostaService.enviarParaAnalise(id);
        propostaService.aprovar(id);
        return id;
    }

    private List<String> destinosNoOutbox(UUID propostaId) {
        return jdbc.queryForList(
            "SELECT payload->>'para' FROM outbox_evento WHERE agregado_id = ? ORDER BY criado_em, id",
            String.class, propostaId);
    }

    private int pendentesNoOutbox(UUID propostaId) {
        Integer pendentes = jdbc.queryForObject(
            "SELECT count(*) FROM outbox_evento WHERE agregado_id = ? AND publicado_em IS NULL",
            Integer.class, propostaId);
        return pendentes;
    }

    /** Lê um campo do JSON da mensagem. O jsonb reordena as chaves: compare campos, nunca o texto. */
    private String campo(ConsumerRecord<String, String> registro, String nome) {
        try {
            return json.readTree(registro.value()).get(nome).asText();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Um consumidor "de fora", com grupo próprio: recebe tudo o que está no tópico desde o início. */
    private static KafkaConsumer<String, String> novoConsumidor() {
        Map<String, Object> configuracao = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "teste-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(configuracao);
    }
}
