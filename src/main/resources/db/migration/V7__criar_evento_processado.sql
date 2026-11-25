-- Inbox do consumidor (fase 5): um evento processado = uma linha.
-- O consumidor insere aqui na mesma transação do efeito (gerar o contrato); se o evento
-- chegar de novo, o INSERT ... ON CONFLICT DO NOTHING insere 0 linhas e nada se repete.
CREATE TABLE evento_processado (
    evento_id     UUID        PRIMARY KEY,
    processado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
