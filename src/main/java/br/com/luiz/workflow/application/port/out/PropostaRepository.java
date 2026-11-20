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

    /**
     * Uma página de propostas, em ordem estável de criação (no banco: {@code criada_em}
     * e, no empate, {@code id}). Alterar uma proposta não muda a posição dela.
     *
     * @param depoisDe id da última proposta da página anterior (o cursor);
     *                 {@code null} = primeira página; cursor que não existe = lista vazia
     * @param limite   quantidade máxima de propostas, já validada pelo caso de uso
     * @return as propostas criadas depois do cursor (sem ele), no máximo {@code limite}
     */
    List<Proposta> listar(UUID depoisDe, int limite);
}
