package br.com.luiz.workflow.adapters.in.rest;

import br.com.luiz.workflow.application.PropostaDesatualizadaException;
import br.com.luiz.workflow.application.PropostaNaoEncontradaException;
import br.com.luiz.workflow.domain.TransicaoInvalidaException;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz as exceções da aplicação e do domínio em ProblemDetail (RFC 9457), só para os
 * controllers deste pacote. Os erros do próprio Spring MVC (400, 405, 415...) ficam com o
 * handler do Spring Boot (spring.mvc.problemdetails.enabled), que tem @Order(0) e por isso
 * é consultado ANTES deste: ver o passo 14 do item 06.
 */
@RestControllerAdvice(basePackageClasses = PropostaRestController.class)
@Order(Ordered.LOWEST_PRECEDENCE)
public class RestErrorHandler {

    /** Cada tipo de problema tem uma seção em docs/problemas.md. */
    private static final String TIPOS = "https://github.com/LFVH/workflow-credito/blob/main/docs/problemas.md#";

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail naoEncontrada(PropostaNaoEncontradaException ex) {
        return problema(HttpStatus.NOT_FOUND, "proposta-nao-encontrada", "Proposta não encontrada", ex);
    }

    /** O pedido é válido, mas conflita com o estado atual: com outro estado, ele passaria. */
    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail transicaoInvalida(TransicaoInvalidaException ex) {
        return problema(HttpStatus.CONFLICT, "transicao-invalida", "Transição de status inválida", ex);
    }

    /** Lock otimista (item 04): outra operação gravou antes. Releia e decida de novo. */
    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail desatualizada(PropostaDesatualizadaException ex) {
        return problema(HttpStatus.CONFLICT, "proposta-desatualizada", "Proposta alterada por outra operação", ex);
    }

    /** Requisição bem formada com dado inválido (valor, prazo, motivo, limite): corrigir e reenviar. */
    @ExceptionHandler
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ProblemDetail dadosInvalidos(IllegalArgumentException ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "dados-invalidos", "Dados inválidos", ex);
    }

    /**
     * O status que vale é o do ProblemDetail; o @ResponseStatus em cada handler existe para o
     * springdoc documentar a resposta. Os dois usam o mesmo HttpStatus.
     */
    private static ProblemDetail problema(HttpStatus status, String tipo, String titulo, RuntimeException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problema.setType(URI.create(TIPOS + tipo));
        problema.setTitle(titulo);
        return problema; // "instance" fica vazio: o Spring preenche com o caminho da requisição
    }
}
