package br.com.luiz.workflow.adapters.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.luiz.workflow.domain.MudancaDeStatus;
import br.com.luiz.workflow.domain.StatusProposta;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureJson // o publicador usa o ObjectMapper do Boot, que o @DataJpaTest não carrega
@Testcontainers(disabledWithoutDocker = true)
@Import(OutboxPublicadorDeEventos.class)
class OutboxPublicadorDeEventosTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired OutboxPublicadorDeEventos publicador;
    @Autowired JdbcTemplate jdbc;

    @Test
    void gravaUmaLinhaPendentePorEventoNaOrdem() {
        UUID propostaId = UUID.randomUUID();

        publicador.publicar(List.of(
            new MudancaDeStatus(propostaId, StatusProposta.RASCUNHO, StatusProposta.EM_ANALISE),
            new MudancaDeStatus(propostaId, StatusProposta.EM_ANALISE, StatusProposta.APROVADA)));

        // A mesma transação do teste: o JdbcTemplate enxerga o que o publicador acabou de inserir.
        List<Map<String, Object>> linhas = jdbc.queryForList("""
                SELECT id, agregado_tipo, tipo_evento, publicado_em, tentativas,
                       payload->>'eventoId'   AS evento_id,
                       payload->>'propostaId' AS proposta_id,
                       payload->>'de'         AS de,
                       payload->>'para'       AS para,
                       payload->>'ocorridoEm' AS ocorrido_em
                  FROM outbox_evento
                 WHERE agregado_id = ?
                 ORDER BY criado_em, id
                """, propostaId);

        assertThat(linhas).hasSize(2);
        // Na ordem em que aconteceram. Com o DEFAULT now(), as duas teriam o mesmo criado_em,
        // e o id aleatório decidiria: este teste falharia em metade das execuções.
        assertThat(linhas).extracting(linha -> linha.get("de")).containsExactly("RASCUNHO", "EM_ANALISE");
        assertThat(linhas).extracting(linha -> linha.get("para")).containsExactly("EM_ANALISE", "APROVADA");
        assertThat(linhas).allSatisfy(linha -> {
            assertThat(linha.get("agregado_tipo")).isEqualTo("Proposta");
            assertThat(linha.get("tipo_evento")).isEqualTo("PropostaStatusAlterado");
            assertThat(linha.get("proposta_id")).isEqualTo(propostaId.toString());
            assertThat(linha.get("evento_id")).isEqualTo(linha.get("id").toString()); // id da linha = eventoId
            assertThat(linha.get("ocorrido_em")).isNotNull();
            assertThat(linha.get("publicado_em")).isNull(); // pendente: quem publica é o relay
            assertThat(linha.get("tentativas")).isEqualTo(0);
        });
    }

    @Test
    void listaVaziaNaoGravaNada() {
        publicador.publicar(List.of());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento", Integer.class)).isZero();
    }
}
