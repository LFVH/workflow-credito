package br.com.luiz.gateway;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Correlation id: toda requisição roteada sai do gateway com um X-Request-Id.
 * Se o cliente mandou um id válido, ele é mantido; senão, o gateway gera um UUID.
 * O mesmo id vai para a aplicação (header da requisição), volta para o cliente
 * (header da resposta) e aparece no log do gateway.
 */
@Component
class CorrelationIdFilter implements GlobalFilter, Ordered {

    static final String HEADER = "X-Request-Id";

    /** Até 64 caracteres seguros para log: letras, dígitos, ponto, sublinhado e hífen. */
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String recebido = exchange.getRequest().getHeaders().getFirst(HEADER);
        String id = (recebido != null && ID_VALIDO.matcher(recebido).matches())
            ? recebido
            : UUID.randomUUID().toString();

        // Os headers da requisição recebida são imutáveis: cria-se uma cópia com o id.
        ServerHttpRequest comId = exchange.getRequest().mutate()
            .headers(headers -> headers.set(HEADER, id))
            .build();

        // Na resposta, antes de seguir: assim o id sai até quando quem responde é o fallback.
        exchange.getResponse().getHeaders().set(HEADER, id);

        long inicio = System.nanoTime();
        return chain.filter(exchange.mutate().request(comId).build())
            .doFinally(sinal -> log.info("{} {} -> {} em {} ms [requestId={}]",
                comId.getMethod(),
                comId.getURI().getRawPath(),
                exchange.getResponse().getStatusCode(),
                (System.nanoTime() - inicio) / 1_000_000,
                id));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // antes de todos, inclusive do circuit breaker
    }
}
