package br.com.luiz.workflow.adapters.out.memory;

import br.com.luiz.workflow.application.PropostaDesatualizadaException;
import br.com.luiz.workflow.application.port.out.PropostaRepository;
import br.com.luiz.workflow.domain.Proposta;
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
 *   <li>guarda e devolve CÓPIAS: alterar o objeto lido não altera o guardado
 *       (no banco, cada leitura monta um objeto novo a partir da linha);</li>
 *   <li>controla a versão como o {@code @Version}: proposta nova entra com 0, cada salvar
 *       incrementa, e salvar com versão diferente da guardada lança
 *       {@link PropostaDesatualizadaException}.</li>
 * </ul>
 */
public class PropostaRepositoryEmMemoria implements PropostaRepository {

    private final Map<UUID, Proposta> banco = new LinkedHashMap<>();

    @Override
    public synchronized Proposta salvar(Proposta proposta) {
        Proposta guardada = banco.get(proposta.id());
        Long versaoGuardada = guardada == null ? null : guardada.versao();

        // O equivalente do "WHERE id = ? AND versao = ?". Objects.equals, nunca == entre Long.
        if (!Objects.equals(proposta.versao(), versaoGuardada)) {
            throw new PropostaDesatualizadaException(proposta.id());
        }

        long novaVersao = versaoGuardada == null ? 0L : versaoGuardada + 1;
        banco.put(proposta.id(), copia(proposta, novaVersao));
        return copia(proposta, novaVersao);
    }

    @Override
    public synchronized Optional<Proposta> buscarPorId(UUID id) {
        return Optional.ofNullable(banco.get(id)).map(p -> copia(p, p.versao()));
    }

    @Override
    public synchronized List<Proposta> listar() {
        return banco.values().stream().map(p -> copia(p, p.versao())).toList();
    }

    /** Snapshot: objeto novo, sem eventos, com o estado de {@code p} e a versão informada. */
    private static Proposta copia(Proposta p, Long versao) {
        return Proposta.reconstituir(p.id(), p.valor(), p.prazoMeses(), p.status(), p.motivo(), versao);
    }
}