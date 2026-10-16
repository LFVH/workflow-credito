package br.com.luiz.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import org.junit.jupiter.params.provider.Arguments;

class StatusPropostaTest {

    private static final Map<StatusProposta, Set<StatusProposta>> DESTINOS_ESPERADOS = Map.of(
        StatusProposta.RASCUNHO, Set.of(StatusProposta.EM_ANALISE, StatusProposta.CANCELADA),
        StatusProposta.EM_ANALISE, Set.of(StatusProposta.PENDENTE_DOCUMENTOS,
            StatusProposta.APROVADA, StatusProposta.REPROVADA, StatusProposta.CANCELADA),
        StatusProposta.PENDENTE_DOCUMENTOS, Set.of(StatusProposta.EM_ANALISE,
            StatusProposta.CANCELADA),
        StatusProposta.APROVADA, Set.of(),
        StatusProposta.REPROVADA, Set.of(),
        StatusProposta.CANCELADA, Set.of()
    );

    static Stream<Arguments> todosOsParesDeTransicao() {
        return Stream.of(StatusProposta.values())
            .flatMap(origem -> Stream.of(StatusProposta.values())
                .map(destino -> Arguments.of(origem, destino,
                    DESTINOS_ESPERADOS.get(origem).contains(destino))));
    }

    @ParameterizedTest
    @MethodSource("todosOsParesDeTransicao")
    void transicoesCorrespondemATabela(StatusProposta origem, StatusProposta destino,
            boolean permitido) {
        assertThat(origem.podeIrPara(destino)).isEqualTo(permitido);
    }

    @ParameterizedTest
    @EnumSource(value = StatusProposta.class, names = {"APROVADA", "REPROVADA", "CANCELADA"})
    void estadosFinaisNaoTemSaida(StatusProposta status) {
        assertThat(status.ehFinal()).isTrue();
    }
}