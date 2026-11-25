package br.com.luiz.workflow.adapters.out.persistence;

import br.com.luiz.workflow.application.port.out.EventosProcessados;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Inbox na tabela evento_processado (V7). A chave primária garante: um evento, uma linha. */
@Component
class EventosProcessadosJdbc implements EventosProcessados {

    private final JdbcTemplate jdbc;

    EventosProcessadosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean registrarSeNovo(UUID eventoId) {
        // ON CONFLICT DO NOTHING: repetido não é erro, é "0 linhas inseridas". Sem ele, a violação
        // da chave primária abortaria a transação inteira e o consumidor ficaria tentando de novo.
        int inseridas = jdbc.update(
            "INSERT INTO evento_processado (evento_id) VALUES (?) ON CONFLICT (evento_id) DO NOTHING",
            eventoId);
        return inseridas == 1;
    }
}
