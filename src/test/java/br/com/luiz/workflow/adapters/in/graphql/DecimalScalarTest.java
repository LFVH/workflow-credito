package br.com.luiz.workflow.adapters.in.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.FloatValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseValueException;
import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class DecimalScalarTest {

    private final Coercing<?, ?> coercing = DecimalScalar.INSTANCE.getCoercing();
    private final GraphQLContext ctx = GraphQLContext.getDefault();
    private final Locale locale = Locale.ROOT;

    @Test
    void variavelEmTextoViraBigDecimalExato() {
        Object valor = coercing.parseValue("1500.50", ctx, locale);
        assertThat(valor).isEqualTo(new BigDecimal("1500.50"));
    }

    @Test
    void variavelInteiraEhAceita() {
        Object valor = coercing.parseValue(1500, ctx, locale);
        assertThat(valor).isEqualTo(new BigDecimal("1500"));
    }

    @Test
    void variavelComPontoFlutuanteEhRejeitada() {
        assertThatThrownBy(() -> coercing.parseValue(1500.5d, ctx, locale))
            .isInstanceOf(CoercingParseValueException.class);
    }

    @Test
    void textoQueNaoEhNumeroEhRejeitado() {
        assertThatThrownBy(() -> coercing.parseValue("mil reais", ctx, locale))
            .isInstanceOf(CoercingParseValueException.class);
    }

    @Test
    void literalDecimalNaQueryEhLidoSemPassarPorDouble() {
        Object valor = coercing.parseLiteral(
            new FloatValue(new BigDecimal("12345678901234.57")), CoercedVariables.emptyVariables(), ctx, locale);
        assertThat(valor).isEqualTo(new BigDecimal("12345678901234.57"));
    }

    @Test
    void literalEmTextoNaQueryEhAceito() {
        Object valor = coercing.parseLiteral(
            new StringValue("0.10"), CoercedVariables.emptyVariables(), ctx, locale);
        assertThat(valor).isEqualTo(new BigDecimal("0.10"));
    }

    @Test
    void saidaEhTextoParaNaoPerderPrecisaoNoCliente() {
        Object saida = coercing.serialize(new BigDecimal("1500.50"), ctx, locale);
        assertThat(saida).isEqualTo("1500.50");
    }
}
