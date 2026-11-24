package br.com.luiz.workflow.adapters.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ContratoJpaRepository extends JpaRepository<ContratoEntity, UUID> {

    /** select ... from contrato where proposta_id = ? */
    Optional<ContratoEntity> findByPropostaId(UUID propostaId);

    /** select ... from contrato where proposta_id in (?, ?, ...) — uma ida ao banco para N propostas. */
    List<ContratoEntity> findByPropostaIdIn(Collection<UUID> propostaIds);
}
