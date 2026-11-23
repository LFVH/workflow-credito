package br.com.luiz.workflow.adapters.in.rest;

import java.math.BigDecimal;

/**
 * Corpo do POST /api/propostas. Só os campos que o cliente pode escolher:
 * status, motivo e versão nascem no domínio (proteção contra mass assignment).
 * O valor aceita texto ("1500.50") ou número (1500.50); nos dois casos o Jackson
 * monta o BigDecimal a partir do texto do JSON, sem passar por double.
 */
public record CriarPropostaRequest(BigDecimal valor, int prazoMeses) {}
