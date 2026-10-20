-- O motivo deixa de ser exclusivo da reprovação: cancelar também passa a exigir.
ALTER TABLE proposta RENAME COLUMN motivo_reprovacao TO motivo;

-- NOT VALID: vale para INSERT/UPDATE daqui em diante, sem barrar propostas
-- canceladas antes desta regra (que ficaram sem motivo).
-- O IS NOT NULL é necessário: CHECK que resulta em NULL é considerado aprovado.
ALTER TABLE proposta
    ADD CONSTRAINT ck_proposta_motivo_obrigatorio
    CHECK (status NOT IN ('REPROVADA', 'CANCELADA') OR (motivo IS NOT NULL AND trim(motivo) <> ''))
    NOT VALID;
