package br.com.luiz.workflow.adapters.in.graphql;

import br.com.luiz.workflow.application.PropostaNaoEncontradaException;
import br.com.luiz.workflow.domain.TransicaoInvalidaException;
import graphql.GraphQLError;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class GraphQlErrorHandler {

    @GraphQlExceptionHandler({TransicaoInvalidaException.class, IllegalArgumentException.class})
    public GraphQLError regraDeNegocio(Exception ex) {
        return GraphQLError.newError().errorType(ErrorType.BAD_REQUEST).message(ex.getMessage()).build();
    }

    @GraphQlExceptionHandler
    public GraphQLError naoEncontrada(PropostaNaoEncontradaException ex) {
        return GraphQLError.newError().errorType(ErrorType.NOT_FOUND).message(ex.getMessage()).build();
    }
}