package br.com.luiz.workflow.adapters.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PropostaJpaRepository extends JpaRepository<PropostaEntity, UUID> {

    /** Primeira página: as mais antigas, com o id desempatando o criada_em. */
    @Query(value = """
            SELECT p.*
              FROM proposta p
             ORDER BY p.criada_em, p.id
             LIMIT :limite
            """, nativeQuery = true)
    List<PropostaEntity> primeiraPagina(@Param("limite") int limite);

    /**
     * Próxima página: as que vêm depois do cursor na ordem (criada_em, id).
     * A subconsulta busca o (criada_em, id) do cursor pela chave primária; se ele não existir,
     * ela devolve zero linhas, a comparação vira NULL e a página sai vazia.
     */
    @Query(value = """
            SELECT p.*
              FROM proposta p
             WHERE (p.criada_em, p.id) > (SELECT c.criada_em, c.id
                                            FROM proposta c
                                           WHERE c.id = :depoisDe)
             ORDER BY p.criada_em, p.id
             LIMIT :limite
            """, nativeQuery = true)
    List<PropostaEntity> paginaApos(@Param("depoisDe") UUID depoisDe, @Param("limite") int limite);
}
