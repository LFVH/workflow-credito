package br.com.luiz.workflow.adapters.out.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Liga o relay do outbox. Uma chave só (workflow.outbox.relay.habilitado=true) ativa
 * o agendamento (@EnableScheduling), o relay e a criação do tópico.
 * Desligada (o padrão; o notebook não tem Kafka), nenhum destes beans existe e a aplicação
 * não fala com o Kafka: o KafkaAdmin só conecta quando existe algum NewTopic.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "workflow.outbox.relay.habilitado", havingValue = "true")
class OutboxRelayConfig {

    /** 3 partições: até 3 consumidores do mesmo grupo em paralelo. 1 réplica: há um broker só. */
    @Bean
    NewTopic topicoStatusDaProposta() {
        return TopicBuilder.name(OutboxRelay.TOPICO).partitions(3).replicas(1).build();
    }

    @Bean
    OutboxRelay outboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
                            PlatformTransactionManager transactionManager,
                            @Value("${workflow.outbox.relay.lote:100}") int tamanhoDoLote) {
        return new OutboxRelay(jdbc, kafka, transactionManager, tamanhoDoLote);
    }
}
