package br.com.luiz.workflow.adapters.in.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.function.Function;

/**
 * Scalar para valores monetários. Nunca passa por double:
 * entra como texto ("1500.50"), inteiro ou literal na query, e sai como texto.
 */
final class DecimalScalar {

    static final GraphQLScalarType INSTANCE = GraphQLScalarType.newScalar()
        .name("Decimal")
        .description("Número decimal exato, serializado como texto. Ex.: \"1500.50\"")
        .coercing(new Coercing<BigDecimal, String>() {

            @Override
            public String serialize(Object resultado, GraphQLContext ctx, Locale locale) {
                if (resultado instanceof BigDecimal valor) {
                    return valor.toPlainString();
                }
                throw new CoercingSerializeException("Esperado BigDecimal, recebido " + resultado);
            }

            @Override
            public BigDecimal parseValue(Object entrada, GraphQLContext ctx, Locale locale) {
                return switch (entrada) {
                    case String texto -> deTexto(texto, CoercingParseValueException::new);
                    case Integer i -> BigDecimal.valueOf(i);
                    case Long l -> BigDecimal.valueOf(l);
                    case BigInteger b -> new BigDecimal(b);
                    case BigDecimal d -> d;
                    default -> throw new CoercingParseValueException(
                        "Envie valores decimais como texto, ex.: \"1500.50\" (recebido " + entrada + ")");
                };
            }

            @Override
            public BigDecimal parseLiteral(Value<?> literal, CoercedVariables variaveis,
                                           GraphQLContext ctx, Locale locale) {
                return switch (literal) {
                    case StringValue s -> deTexto(s.getValue(), CoercingParseLiteralException::new);
                    case IntValue i -> new BigDecimal(i.getValue());
                    case FloatValue f -> f.getValue(); // o parser do GraphQL já entrega BigDecimal
                    default -> throw new CoercingParseLiteralException("Valor decimal inválido: " + literal);
                };
            }
        })
        .build();

    private static <E extends RuntimeException> BigDecimal deTexto(
            String texto, Function<String, E> erro) {
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException ex) {
            throw erro.apply("Valor decimal inválido: \"" + texto + "\"");
        }
    }

    private DecimalScalar() {}
}
