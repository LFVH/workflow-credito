-- Paginação por cursor (keyset): ORDER BY criada_em, id + WHERE (criada_em, id) > (...) LIMIT n.
-- O id entra no índice (e na ordem) como desempate: DEFAULT now() devolve o início da
-- transação, igual para todos os INSERTs dela, então criada_em sozinho empata.
CREATE INDEX idx_proposta_criada_em_id ON proposta (criada_em, id);
