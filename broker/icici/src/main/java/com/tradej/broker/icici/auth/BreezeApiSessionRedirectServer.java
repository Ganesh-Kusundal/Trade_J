package com.tradej.broker.icici.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Listens on the ICICI app redirect URL (e.g. {@code http://127.0.0.1:9080/api}) and captures
 * {@code apisession} from the login callback query string.
 */
final class BreezeApiSessionRedirectServer implements AutoCloseable {

    private static final byte[] SUCCESS_RESPONSE = """
            <html><body>
            <h1>ICICI session captured</h1>
            <p>You can close this window.</p>
            </body></html>
            """.getBytes(StandardCharsets.UTF_8);

    private final HttpServer server;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicReference<String> apiSession = new AtomicReference<>();
    private final AtomicReference<String> error = new AtomicReference<>();

    BreezeApiSessionRedirectServer(int port, String redirectPath) throws IOException {
        String path = redirectPath.startsWith("/") ? redirectPath : "/" + redirectPath;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext(path, this::handleCallback);
        server.setExecutor(null);
    }

    void start() {
        server.start();
    }

    String waitForApiSession(long timeoutMs) {
        try {
            boolean received = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!received) {
                throw new BreezeBrowserAuthException("Timed out waiting for ICICI redirect callback");
            }
            String failure = error.get();
            if (failure != null) {
                throw new BreezeBrowserAuthException(failure);
            }
            String session = apiSession.get();
            if (session == null || session.isBlank()) {
                throw new BreezeBrowserAuthException("Redirect received without apisession parameter");
            }
            return session;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BreezeBrowserAuthException("Interrupted while waiting for ICICI redirect", ex);
        }
    }

    String pollApiSession() {
        return apiSession.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handleCallback(HttpExchange exchange) {
        try {
            String session = BreezeApiSessionUrlParser.parseApiSession(exchange.getRequestURI().toString());
            if (session == null) {
                error.set("Redirect callback missing apisession query parameter");
                sendResponse(exchange, 400, "Missing apisession".getBytes(StandardCharsets.UTF_8));
            } else {
                apiSession.set(session);
                sendResponse(exchange, 200, SUCCESS_RESPONSE);
            }
            latch.countDown();
        } catch (Exception ex) {
            error.set("Redirect callback failed: " + ex.getMessage());
            latch.countDown();
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, byte[] body) throws IOException {
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
