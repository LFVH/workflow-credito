package br.com.luiz.workflow.adapters.out.persistence;

import br.com.luiz.workflow.application.port.out.PropostaRepository;
import br.com.luiz.workflow.domain.Proposta;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PropostaRepositoryJpa implements PropostaRepository {

    private final PropostaJpaRepository jpa;

    PropostaRepositoryJpa(PropostaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Proposta salvar(Proposta proposta) {
        return jpa.save(PropostaEntity.de(proposta)).paraDominio();
    }

    @Override
    public Optional<Proposta> buscarPorId(UUID id) {
        return jpa.findById(id).map(PropostaEntity::paraDominio);
    }

    @Override
    public List<Proposta> listar() {
        return jpa.findAllByOrderByCriadaEmAsc().stream().map(PropostaEntity::paraDominio).toList();
    }
}