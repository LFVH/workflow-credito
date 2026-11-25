package br.com.luiz.workflow.application;

import br.com.luiz.workflow.application.port.out.PropostaRepository;
import br.com.luiz.workflow.application.port.out.PublicadorDeEventos;
import br.com.luiz.workflow.application.port.out.Transacao;
import br.com.luiz.workflow.domain.MudancaDeStatus;
import br.com.luiz.workflow.domain.Proposta;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Casos de uso da proposta. Java puro, sem anotações do Spring: a transação vem do
 * port {@link Transacao} e os eventos saem pelo port {@link PublicadorDeEventos}
 * (que, em produção, grava no outbox dentro da mesma transação).
 */
public class PropostaService {

    /** Tamanho da página quando o cliente não informa o limite. */
    public static final int LIMITE_PADRAO = 20;

    /** Maior página aceita: protege memória, banco e rede de um "me dá tudo". */
    public static final int LIMITE_MAXIMO = 100;

    private final PropostaRepository repository;
    private final PublicadorDeEventos publicador;
    private final Transacao transacao;

    public PropostaService(PropostaRepository repository, PublicadorDeEventos publicador,
                           Transacao transacao) {
        this.repository = repository;
        this.publicador = publicador;
        this.transacao = transacao;
    }

    public Proposta criar(BigDecimal valor, int prazoMeses) {
        Proposta nova = Proposta.nova(valor, prazoMeses); // valida ANTES de abrir a transação
        return transacao.executar(() -> repository.salvar(nova));
    }

    public Proposta enviarParaAnalise(UUID id)  { return transicionar(id, Proposta::enviarParaAnalise); }
    public Proposta pedirDocumentos(UUID id)    { return transicionar(id, Proposta::pedirDocumentos); }
    public Proposta aprovar(UUID id)            { return transicionar(id, Proposta::aprovar); }
    public Proposta reprovar(UUID id, String motivo) {
        return transicionar(id, proposta -> proposta.reprovar(motivo));
    }
    public Proposta cancelar(UUID id, String motivo) {
        return transicionar(id, proposta -> proposta.cancelar(motivo));
    }

    /** Leitura de uma consulta só: atômica por natureza, fica fora da transação. */
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

    /**
     * Buscar, mudar, gravar e publicar numa transação só. A ordem importa:
     * os eventos são retirados do objeto ALTERADO (o salvar devolve uma cópia nova, sem eventos)
     * e publicados DEPOIS do salvar: um conflito de versão no salvar não deixa evento
     * de uma mudança que não aconteceu.
     */
    private Proposta transicionar(UUID id, Consumer<Proposta> acao) {
        return transacao.executar(() -> {
            Proposta proposta = repository.buscarPorId(id)
                .orElseThrow(() -> new PropostaNaoEncontradaException(id));
            acao.accept(proposta);
            List<MudancaDeStatus> eventos = proposta.retirarEventos();
            Proposta salva = repository.salvar(proposta);
            publicador.publicar(eventos);
            return salva;
        });
    }
}
