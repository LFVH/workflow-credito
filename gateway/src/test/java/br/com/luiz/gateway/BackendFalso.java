package br.com.luiz.gateway;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Faz o papel da aplicação WorkFlow nos testes do gateway: um servidor HTTP do
 * próprio JDK que responde 200 com um JSON fixo e guarda cada requisição recebida.
 */
final class BackendFalso implements AutoCloseable {

    /** O que chegou do gateway. {@code caminho} inclui a query string. */
    record Recebida(String metodo, String caminho, Headers headers, String corpo) {}

    private static final byte[] RESPOSTA =
        "{\"origem\":\"backend-falso\"}".getBytes(StandardCharsets.UTF_8);

    private final HttpServer servidor;
    private final BlockingQueue<Recebida> recebidas = new LinkedBlockingQueue<>();

    private BackendFalso(HttpServer servidor) {
        this.servidor = servidor;
        this.servidor.createContext("/", this::responder);
        this.servidor.start();
    }

    /** Sobe numa porta livre de 127.0.0.1 (porta 0 = o sistema operacional escolhe). */
    static BackendFalso iniciar() {
        try {
            return new BackendFalso(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    private void responder(HttpExchange troca) throws IOException {
        String corpo;
        try (InputStream entrada = troca.getRequestBody()) {
            corpo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
        recebidas.add(new Recebida(
            troca.getRequestMethod(),
            troca.getRequestURI().toString(),
            new Headers(troca.getRequestHeaders()),
            corpo));

        troca.getResponseHeaders().set("Content-Type", "application/json");
        troca.sendResponseHeaders(200, RESPOSTA.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(RESPOSTA);
        }
    }

    /** A próxima requisição recebida; falha o teste se nada chegar em 5 s. */
    Recebida proxima() {
        Recebida recebida = esperar(5_000);
        if (recebida == null) {
            throw new AssertionError("O backend falso não recebeu nenhuma requisição em 5 s");
        }
        return recebida;
    }

    /** true se nada chegou em 300 ms: prova de que o gateway NÃO encaminhou. */
    boolean nadaRecebido() {
        return esperar(300) == null;
    }

    void esquecer() {
        recebidas.clear();
    }

    private Recebida esperar(long milissegundos) {
        try {
            return recebidas.poll(milissegundos, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
