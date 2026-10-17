package br.com.luiz.workflow.application;

import br.com.luiz.workflow.application.port.out.PropostaRepository;
import br.com.luiz.workflow.domain.Proposta;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class PropostaService {

    private final PropostaRepository repository;

    public PropostaService(PropostaRepository repository) {
        this.repository = repository;
    }

    public Proposta criar(BigDecimal valor, int prazoMeses) {
        return repository.salvar(Proposta.nova(valor, prazoMeses));
    }

    public Proposta enviarParaAnalise(UUID id)  { return transicionar(id, Proposta::enviarParaAnalise); }
    public Proposta pedirDocumentos(UUID id)    { return transicionar(id, Proposta::pedirDocumentos); }
    public Proposta aprovar(UUID id)            { return transicionar(id, Proposta::aprovar); }
    public Proposta cancelar(UUID id)           { return transicionar(id, Proposta::cancelar); }
    public Proposta reprovar(UUID id, String motivo) {
        return transicionar(id, proposta -> proposta.reprovar(motivo));
    }

    public Optional<Proposta> buscar(UUID id) {
        return repository.buscarPorId(id);
    }

    public List<Proposta> listar() {
        return repository.listar();
    }

    private Proposta transicionar(UUID id, Consumer<Proposta> acao) {
        Proposta proposta = repository.buscarPorId(id)
            .orElseThrow(() -> new PropostaNaoEncontradaException(id));
        acao.accept(proposta);
        return repository.salvar(proposta);
    }
}