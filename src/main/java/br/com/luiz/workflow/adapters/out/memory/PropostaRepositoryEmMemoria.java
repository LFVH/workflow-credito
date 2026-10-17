package br.com.luiz.workflow.adapters.out.memory;

import br.com.luiz.workflow.application.port.out.PropostaRepository;
import br.com.luiz.workflow.domain.Proposta;
import java.util.*;

public class PropostaRepositoryEmMemoria implements PropostaRepository {

    private final Map<UUID, Proposta> banco = new LinkedHashMap<>();

    @Override
    public synchronized Proposta salvar(Proposta proposta) {
        banco.put(proposta.id(), proposta);
        return proposta;
    }

    @Override
    public synchronized Optional<Proposta> buscarPorId(UUID id) {
        return Optional.ofNullable(banco.get(id));
    }

    @Override
    public synchronized List<Proposta> listar() {
        return new ArrayList<>(banco.values());
    }
}