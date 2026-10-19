CREATE TABLE proposta (
    id                UUID PRIMARY KEY,
    valor             NUMERIC(15,2) NOT NULL CHECK (valor > 0),
    prazo_meses       INTEGER       NOT NULL CHECK (prazo_meses BETWEEN 1 AND 120),
    status            VARCHAR(30)   NOT NULL CHECK (status IN
        ('RASCUNHO','EM_ANALISE','PENDENTE_DOCUMENTOS','APROVADA','REPROVADA','CANCELADA')),
    motivo_reprovacao TEXT,
    criada_em         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_proposta_status ON proposta (status);