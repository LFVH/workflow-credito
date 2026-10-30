package br.com.luiz.workflow.application.port.out;

import br.com.luiz.workflow.domain.Proposta;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropostaRepository {

    /**
     * Grava a proposta e devolve a cópia gravada, já com a versão nova.
     * Proposta com versão {@code null} (nova) é inserida. Proposta com versão só é gravada
     * se essa ainda for a versão armazenada; se outra operação gravou antes, lança
     * {@link br.com.luiz.workflow.application.PropostaDesatualizadaException}.
     * Quem chama deve continuar com o objeto devolvido, não com o que passou.
     */
    Proposta salvar(Proposta proposta);
    Optional<Proposta> buscarPorId(UUID id);
    List<Proposta> listar();
}