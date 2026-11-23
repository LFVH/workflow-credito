package br.com.luiz.workflow.application;

import br.com.luiz.workflow.application.port.out.PropostaRepository;
import br.com.luiz.workflow.domain.Proposta;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class PropostaService {

    /** Tamanho da página quando o cliente não informa o limite. */
    public static final int LIMITE_PADRAO = 20;

    /** Maior página aceita: protege memória, banco e rede de um "me dá tudo". */
    public static final int LIMITE_MAXIMO = 100;

    private final PropostaRepository repository;

    public PropostaService(PropostaRepository repository) {
        this.repository = repository;
    }

    public Proposta criar(BigDecimal valor, int prazoMeses) {
        return repository.salvar(Proposta.nova(valor, prazoMeses));
    }

    public Proposta enviarParaAnalise(UUID id)  { return transicionar(id, proposta -> proposta.enviarParaAnalise()); }
    public Proposta pedirDocumentos(UUID id)    { return transicionar(id, proposta -> proposta.pedirDocumentos()); }
    public Proposta aprovar(UUID id)            { return transicionar(id, proposta -> proposta.aprovar()); }
    public Proposta reprovar(UUID id, String motivo) {
        return transicionar(id, proposta -> proposta.reprovar(motivo));
    }
    public Proposta cancelar(UUID id, String motivo) {
        return transicionar(id, proposta -> proposta.cancelar(motivo));
    }

    public Optional<Proposta> buscar(UUID id) {
        return repository.buscarPorId(id);
    }

    /**
     * Uma página de propostas na ordem de criação (paginação por cursor).
     *
     * @param depoisDe id da última proposta da página anterior; {@code null} = primeira página
     * @param limite   tamanho da página; {@code null} = {@link #LIMITE_PADRAO}
     * @throws IllegalArgumentException se o limite estiver fora de 1..{@link #LIMITE_MAXIMO}
     */
    public List<Proposta> listar(UUID depoisDe, Integer limite) {
        int tamanho = (limite == null) ? LIMITE_PADRAO : limite;
        if (tamanho < 1 || tamanho > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("Limite deve estar entre 1 e " + LIMITE_MAXIMO);
        }
        return repository.listar(depoisDe, tamanho);
    }

    private Proposta transicionar(UUID id, Consumer<Proposta> acao) {
        Proposta proposta = repository.buscarPorId(id)
            .orElseThrow(() -> new PropostaNaoEncontradaException(id));
        acao.accept(proposta);
        return repository.salvar(proposta);
    }
}
