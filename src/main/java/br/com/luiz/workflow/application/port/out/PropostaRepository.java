package br.com.luiz.workflow.application.port.out;

import br.com.luiz.workflow.domain.Proposta;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropostaRepository {
    Proposta salvar(Proposta proposta);
    Optional<Proposta> buscarPorId(UUID id);
    List<Proposta> listar();
}