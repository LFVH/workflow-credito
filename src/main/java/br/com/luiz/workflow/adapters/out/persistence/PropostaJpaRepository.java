package br.com.luiz.workflow.adapters.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PropostaJpaRepository extends JpaRepository<PropostaEntity, UUID> {
    List<PropostaEntity> findAllByOrderByCriadaEmAsc();
}