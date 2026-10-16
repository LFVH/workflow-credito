// MudancaDeStatus.java
package br.com.luiz.workflow.domain;

import java.util.UUID;

public record MudancaDeStatus(UUID propostaId, StatusProposta de, StatusProposta para) {}