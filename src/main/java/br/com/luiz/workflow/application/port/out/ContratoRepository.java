package br.com.luiz.workflow.application.port.out;

import br.com.luiz.workflow.domain.Contrato;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContratoRepository {

    /**
     * Grava o contrato e devolve a cópia gravada, já com a versão nova.
     * Contrato com versão {@code null} (novo) é inserido, e só pode existir um contrato por proposta.
     * Contrato com versão só é gravado se essa ainda for a versão armazenada; se outra operação
     * gravou antes, lança {@link br.com.luiz.workflow.application.ContratoDesatualizadoException}.
     * Quem chama deve continuar com o objeto devolvido, não com o que passou.
     */
    Contrato salvar(Contrato contrato);

    Optional<Contrato> buscarPorId(UUID id);

    /** O contrato gerado a partir da proposta, se houver (no máximo um). */
    Optional<Contrato> buscarPorPropostaId(UUID propostaId);

    /**
     * Os contratos das propostas informadas, numa consulta só: é o que evita o N+1.
     * Devolve só os que existem (proposta sem contrato simplesmente não aparece),
     * sem ordem garantida; quem chama casa os resultados pelo {@code propostaId}.
     */
    List<Contrato> buscarPorPropostaIds(Collection<UUID> propostaIds);
}
