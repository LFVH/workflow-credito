package br.com.luiz.workflow.adapters.in.rest;

/**
 * Corpo de /reprovacao e /cancelamento. A regra "motivo obrigatório" mora no domínio:
 * aqui só se transporta o texto.
 */
public record MotivoRequest(String motivo) {}
