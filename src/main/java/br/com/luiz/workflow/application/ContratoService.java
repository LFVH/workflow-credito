package br.com.luiz.workflow.application;

import br.com.luiz.workflow.application.port.out.ContratoRepository;
import br.com.luiz.workflow.application.port.out.PropostaRepository;
import br.com.luiz.workflow.application.port.out.Transacao;
import br.com.luiz.workflow.domain.Contrato;
import br.com.luiz.workflow.domain.Proposta;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ContratoService {

    private final ContratoRepository contratos;
    private final PropostaRepository propostas;
    private final Transacao transacao;

    public ContratoService(ContratoRepository contratos, PropostaRepository propostas,
                           Transacao transacao) {
        this.contratos = contratos;
        this.propostas = propostas;
        this.transacao = transacao;
    }

    /**
     * Gera o contrato da proposta aprovada. IDEMPOTENTE: se a proposta já tem contrato,
     * devolve o existente, no estado em que estiver, sem gravar nada. Chamar de novo com
     * a mesma proposta (clique repetido, mensagem Kafka reentregue) é seguro.
     * Roda numa transação; se já houver uma (o consumidor Kafka abre a dele), participa dela.
     * O UNIQUE (proposta_id) do banco continua sendo a última garantia contra duas
     * chamadas simultâneas: a transação não elimina essa corrida, o UNIQUE sim.
     *
     * @throws PropostaNaoEncontradaException se a proposta não existe
     * @throws IllegalArgumentException se a proposta não está APROVADA
     */
    public Contrato gerarPara(UUID propostaId) {
        // orElseGet, NUNCA orElse: o argumento do orElse é avaliado sempre, mesmo quando
        // o contrato já existe, e geraria (e gravaria) um segundo contrato.
        return transacao.executar(() -> contratos.buscarPorPropostaId(propostaId)
            .orElseGet(() -> contratos.salvar(Contrato.gerarPara(proposta(propostaId)))));
    }

    public Contrato assinar(UUID id)  { return transicionar(id, Contrato::assinar); }
    public Contrato ativar(UUID id)   { return transicionar(id, Contrato::ativar); }
    public Contrato liquidar(UUID id) { return transicionar(id, Contrato::liquidar); }
    public Contrato cancelar(UUID id, String motivo) {
        return transicionar(id, contrato -> contrato.cancelar(motivo));
    }

    public Optional<Contrato> buscar(UUID id) {
        return contratos.buscarPorId(id);
    }

    public Optional<Contrato> buscarPorProposta(UUID propostaId) {
        return contratos.buscarPorPropostaId(propostaId);
    }

    /** Contratos de várias propostas numa consulta só (só os que existem). */
    public List<Contrato> buscarPorPropostas(Collection<UUID> propostaIds) {
        if (propostaIds.isEmpty()) {
            return List.of(); // nem vai ao banco: "IN ()" vazio nem é SQL válido
        }
        return contratos.buscarPorPropostaIds(propostaIds);
    }

    private Proposta proposta(UUID propostaId) {
        return propostas.buscarPorId(propostaId)
            .orElseThrow(() -> new PropostaNaoEncontradaException(propostaId));
    }

    private Contrato transicionar(UUID id, Consumer<Contrato> acao) {
        return transacao.executar(() -> {
            Contrato contrato = contratos.buscarPorId(id)
                .orElseThrow(() -> new ContratoNaoEncontradoException(id));
            acao.accept(contrato);
            return contratos.salvar(contrato);
        });
    }
}
