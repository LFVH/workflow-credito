package br.com.luiz.workflow.adapters.out.persistence;

import br.com.luiz.workflow.application.ContratoDesatualizadoException;
import br.com.luiz.workflow.application.port.out.ContratoRepository;
import br.com.luiz.workflow.domain.Contrato;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
class ContratoRepositoryJpa implements ContratoRepository {

    private final ContratoJpaRepository jpa;

    ContratoRepositoryJpa(ContratoJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * saveAndFlush, e não save: o INSERT/UPDATE vai ao banco AGORA, dentro deste try.
     * O conflito de versão é traduzido aqui, e a entidade devolvida já traz a versão nova.
     */
    @Override
    public Contrato salvar(Contrato contrato) {
        try {
            return jpa.saveAndFlush(ContratoEntity.de(contrato)).paraDominio();
        } catch (ObjectOptimisticLockingFailureException ex) {
            ContratoDesatualizadoException desatualizado = new ContratoDesatualizadoException(contrato.id());
            desatualizado.initCause(ex); // guarda o rastro técnico para o log, sem expor JPA à aplicação
            throw desatualizado;
        }
    }

    @Override
    public Optional<Contrato> buscarPorId(UUID id) {
        return jpa.findById(id).map(ContratoEntity::paraDominio);
    }

    @Override
    public Optional<Contrato> buscarPorPropostaId(UUID propostaId) {
        return jpa.findByPropostaId(propostaId).map(ContratoEntity::paraDominio);
    }

    @Override
    public List<Contrato> buscarPorPropostaIds(Collection<UUID> propostaIds) {
        if (propostaIds.isEmpty()) {
            return List.of(); // nada a buscar: não vai ao banco
        }
        return jpa.findByPropostaIdIn(propostaIds).stream().map(ContratoEntity::paraDominio).toList();
    }
}
