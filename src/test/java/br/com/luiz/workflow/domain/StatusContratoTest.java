package br.com.luiz.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class StatusContratoTest {

    /** A tabela do negócio, escrita de forma independente do código de produção. */
    private static final Map<StatusContrato, Set<StatusContrato>> DESTINOS_ESPERADOS = Map.of(
        StatusContrato.GERADO, Set.of(StatusContrato.ASSINADO, StatusContrato.CANCELADO),
        StatusContrato.ASSINADO, Set.of(StatusContrato.ATIVO, StatusContrato.CANCELADO),
        StatusContrato.ATIVO, Set.of(StatusContrato.LIQUIDADO),
        StatusContrato.LIQUIDADO, Set.of(),
        StatusContrato.CANCELADO, Set.of()
    );

    /** Todos os 25 pares (origem, destino), com a resposta esperada para cada um. */
    static Stream<Arguments> todosOsParesDeTransicao() {
        return Stream.of(StatusContrato.values())
            .flatMap(origem -> Stream.of(StatusContrato.values())
                .map(destino -> Arguments.of(origem, destino,
                    DESTINOS_ESPERADOS.get(origem).contains(destino))));
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @MethodSource("todosOsParesDeTransicao")
    void transicoesCorrespondemATabela(StatusContrato origem, StatusContrato destino, boolean permitido) {
        assertThat(origem.podeIrPara(destino)).isEqualTo(permitido);
    }

    @ParameterizedTest
    @EnumSource(value = StatusContrato.class, names = {"LIQUIDADO", "CANCELADO"})
    void estadosFinaisNaoTemSaida(StatusContrato status) {
        assertThat(status.ehFinal()).isTrue();
        assertThat(status.destinosPermitidos()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = StatusContrato.class, names = {"LIQUIDADO", "CANCELADO"},
        mode = EnumSource.Mode.EXCLUDE)
    void estadosNaoFinaisTemSaida(StatusContrato status) {
        assertThat(status.ehFinal()).isFalse();
    }
}
