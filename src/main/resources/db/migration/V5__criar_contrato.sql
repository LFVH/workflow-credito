-- Contrato: agregado separado da proposta, que ele referencia só pelo id.
-- Copia valor e prazo da proposta aprovada (o que foi assinado não muda depois).
CREATE TABLE contrato (
    id           UUID          PRIMARY KEY,
    proposta_id  UUID          NOT NULL,
    valor        NUMERIC(15,2) NOT NULL CHECK (valor > 0),
    prazo_meses  INTEGER       NOT NULL CHECK (prazo_meses BETWEEN 1 AND 120),
    status       VARCHAR(20)   NOT NULL CHECK (status IN
        ('GERADO','ASSINADO','ATIVO','LIQUIDADO','CANCELADO')),
    motivo       TEXT,
    versao       BIGINT        NOT NULL DEFAULT 0,
    criado_em    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Garantia final de UM contrato por proposta, mesmo com duas chamadas simultâneas.
    -- O UNIQUE cria um índice btree em proposta_id, que também atende os
    -- "WHERE proposta_id = ?" e "WHERE proposta_id IN (...)" da aplicação.
    CONSTRAINT uq_contrato_proposta UNIQUE (proposta_id),

    -- Sem ON DELETE CASCADE: proposta com contrato não pode ser apagada.
    CONSTRAINT fk_contrato_proposta FOREIGN KEY (proposta_id) REFERENCES proposta (id),

    -- Como na V2: CHECK que resulta em NULL é aprovado, por isso o IS NOT NULL explícito.
    CONSTRAINT ck_contrato_motivo_cancelamento
        CHECK (status <> 'CANCELADO' OR (motivo IS NOT NULL AND trim(motivo) <> ''))
);
