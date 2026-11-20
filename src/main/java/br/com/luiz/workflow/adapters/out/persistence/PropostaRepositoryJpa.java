package br.com.luiz.workflow.adapters.out.persistence;

import br.com.luiz.workflow.application.PropostaDesatualizadaException;
import br.com.luiz.workflow.application.port.out.PropostaRepository;
import br.com.luiz.workflow.domain.Proposta;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
class PropostaRepositoryJpa implements PropostaRepository {

    private final PropostaJpaRepository jpa;

    PropostaRepositoryJpa(PropostaJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * saveAndFlush, e não save: o INSERT/UPDATE vai ao banco AGORA, dentro deste try.
     * Assim o conflito de versão é traduzido aqui (e não estoura depois, no commit, fora
     * do adaptador), e a entidade devolvida já traz a versão incrementada.
     */
    @Override
    public Proposta salvar(Proposta proposta) {
        try {
            return jpa.saveAndFlush(PropostaEntity.de(proposta)).paraDominio();
        } catch (ObjectOptimisticLockingFailureException ex) {
            PropostaDesatualizadaException desatualizada = new PropostaDesatualizadaException(proposta.id());
            desatualizada.initCause(ex); // guarda o rastro técnico para o log, sem expor JPA à aplicação
            throw desatualizada;
        }
    }

    @Override
    public Optional<Proposta> buscarPorId(UUID id) {
        return jpa.findById(id).map(PropostaEntity::paraDominio);
    }

    /**
     * Duas queries em vez de uma com "(:depoisDe IS NULL OR ...)": com o parâmetro nulo,
     * o driver manda um valor sem tipo e o Postgres não consegue resolvê-lo; e o OR
     * atrapalharia o planejador. Cada query aqui tem um plano simples pelo índice
     * (criada_em, id) da V4.
     */
    @Override
    public List<Proposta> listar(UUID depoisDe, int limite) {
        List<PropostaEntity> pagina = (depoisDe == null)
            ? jpa.primeiraPagina(limite)
            : jpa.paginaApos(depoisDe, limite);
        return pagina.stream().map(PropostaEntity::paraDominio).toList();
    }
}
