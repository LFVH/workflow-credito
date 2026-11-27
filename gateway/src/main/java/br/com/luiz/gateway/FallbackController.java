package br.com.luiz.gateway;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * Destino do circuit breaker (fallbackUri: forward:/fallback/aplicacao) quando a aplicação
 * está fora do ar, demora demais ou o circuito está aberto. Responde 503 em ProblemDetail
 * (RFC 9457), no mesmo formato da API REST.
 */
@RestController
class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    /** Mesmo padrão de "type" da API REST (item 06): uma seção por problema em docs/problemas.md. */
    private static final URI TIPO = URI.create(
        "https://github.com/LFVH/workflow-credito/blob/main/docs/problemas.md#servico-indisponivel");

    /** Igual ao wait-duration-in-open-state do application.yml: quando o circuito testa de novo. */
    private static final String TENTE_DE_NOVO_EM_SEGUNDOS = "10";

    // Sem método HTTP: o forward mantém o método original (GET, POST...), e todos caem aqui.
    @RequestMapping("/fallback/aplicacao")
    public ResponseEntity<ProblemDetail> aplicacaoIndisponivel(ServerWebExchange exchange) {
        Throwable causa = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        String requestId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER);
        // A causa vai para o log (com o id), nunca para o corpo da resposta.
        log.warn("Aplicação indisponível [requestId={}]: {}", requestId, String.valueOf(causa));

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
            "A aplicação está fora do ar ou demorou demais para responder. Tente de novo em alguns segundos.");
        problema.setType(TIPO);
        problema.setTitle("Serviço indisponível");
        problema.setProperty("requestId", requestId);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .header(HttpHeaders.RETRY_AFTER, TENTE_DE_NOVO_EM_SEGUNDOS)
            .body(problema);
    }
}
