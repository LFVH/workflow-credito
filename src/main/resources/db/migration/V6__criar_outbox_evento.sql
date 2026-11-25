-- Outbox transacional (fase 5). Cada mudança de status grava aqui um evento, na MESMA
-- transação do UPDATE da proposta. Um relay lê as linhas pendentes e publica no Kafka.
CREATE TABLE outbox_evento (
    id            UUID         PRIMARY KEY,               -- = eventoId (vai no payload e no header)
    agregado_tipo VARCHAR(50)  NOT NULL,                  -- 'Proposta' (a tabela serve a qualquer agregado)
    agregado_id   UUID         NOT NULL,                  -- vira a chave da mensagem: ordem por proposta
    tipo_evento   VARCHAR(100) NOT NULL,                  -- 'PropostaStatusAlterado'
    payload       JSONB        NOT NULL,                  -- a mensagem, já no formato publicado
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),    -- o adaptador grava clock_timestamp()
    publicado_em  TIMESTAMPTZ,                            -- NULL = pendente
    tentativas    INTEGER      NOT NULL DEFAULT 0         -- falhas de envio (alerta, mensagem envenenada)
);

-- Sem chave estrangeira para proposta: o outbox é genérico e não deve impedir nem prender nada.

-- O índice parcial guarda só as pendentes. A fila do relay continua pequena mesmo com
-- milhões de eventos já publicados na tabela.
CREATE INDEX idx_outbox_evento_pendente ON outbox_evento (criado_em) WHERE publicado_em IS NULL;
