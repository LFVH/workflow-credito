package br.com.luiz.workflow.adapters.out.memory;

import br.com.luiz.workflow.application.ContratoDesatualizadoException;
import br.com.luiz.workflow.application.port.out.ContratoRepository;
import br.com.luiz.workflow.domain.Contrato;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação do port em memória, para testes rápidos sem banco.
 * Imita o que importa do adaptador JPA:
 * <ul>
 *   <li>guarda e devolve CÓPIAS (no banco, cada leitura monta um objeto novo);</li>
 *   <li>controla a versão como o {@code @Version}: contrato novo entra com 0, cada salvar
 *       incrementa, e salvar com versão diferente da guardada lança
 *       {@link ContratoDesatualizadoException};</li>
 *   <li>recusa um segundo contrato para a mesma proposta, como o UNIQUE (proposta_id) da V5.</li>
 * </ul>
 */
public class ContratoRepositoryEmMemoria implements ContratoRepository {

    private final Map<UUID, Contrato> banco = new LinkedHashMap<>();

    @Override
    public synchronized Contrato salvar(Contrato contrato) {
        Contrato guardado = banco.get(contrato.id());
        Long versaoGuardada = guardado == null ? null : guardado.versao();

        // O equivalente do "WHERE id = ? AND versao = ?". Objects.equals, nunca == entre Long.
        if (!Objects.equals(contrato.versao(), versaoGuardada)) {
            throw new ContratoDesatualizadoException(contrato.id());
        }
        // O equivalente do UNIQUE (proposta_id): só vale para contrato novo.
        if (guardado == null && buscarPorPropostaId(contrato.propostaId()).isPresent()) {
            throw new IllegalStateException("Já existe contrato para a proposta " + contrato.propostaId());
        }

        long novaVersao = versaoGuardada == null ? 0L : versaoGuardada + 1;
        banco.put(contrato.id(), copia(contrato, novaVersao));
        return copia(contrato, novaVersao);
    }

    @Override
    public synchronized Optional<Contrato> buscarPorId(UUID id) {
        return Optional.ofNullable(banco.get(id)).map(c -> copia(c, c.versao()));
    }

    @Override
    public synchronized Optional<Contrato> buscarPorPropostaId(UUID propostaId) {
        return banco.values().stream()
            .filter(c -> c.propostaId().equals(propostaId))
            .findFirst()
            .map(c -> copia(c, c.versao()));
    }

    @Override
    public synchronized List<Contrato> buscarPorPropostaIds(Collection<UUID> propostaIds) {
        return banco.values().stream()
            .filter(c -> propostaIds.contains(c.propostaId()))
            .map(c -> copia(c, c.versao()))
            .toList();
    }

    /** Snapshot: objeto novo, com o estado de {@code c} e a versão informada. */
    private static Contrato copia(Contrato c, Long versao) {
        return Contrato.reconstituir(c.id(), c.propostaId(), c.valor(), c.prazoMeses(),
            c.status(), c.motivo(), versao);
    }
}
