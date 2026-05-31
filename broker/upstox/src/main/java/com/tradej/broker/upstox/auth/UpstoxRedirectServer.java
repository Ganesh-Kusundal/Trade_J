package com.tradej.broker.upstox.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Temporary local HTTP server that listens for the OAuth redirect callback.
 * <p>
 * After the user authorizes in the browser, Upstox redirects to {@code redirectUri}
 * with {@code ?code=AUTHORIZATION_CODE&state=STATE}. This server captures the code.
 * <p>
 * In headless mode, use {@link #authorizationCode()} directly.
 */
public final class UpstoxRedirectServer implements AutoCloseable {

    private final HttpServer server;
    private final int port;
    private final String redirectPath;
    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile String authorizationCode;
    private volatile String state;
    private volatile Throwable error;

    private static final byte[] SUCCESS_RESPONSE = """
            <html><body>
            <h1>Authorization successful!</h1>
            <p>You can close this window and return to the terminal.</p>
            </body></html>
            """.getBytes(StandardCharsets.UTF_8);

    private static final byte[] ERROR_RESPONSE = """
            <html><body>
            <h1>Authorization failed</h1>
            <p>Check the error parameter in the URL and try again.</p>
            </body></html>
            """.getBytes(StandardCharsets.UTF_8);

    public UpstoxRedirectServer(int port, String redirectPath) throws IOException {
        this.port = port;
        this.redirectPath = redirectPath;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(redirectPath, this::handleCallback);
        server.setExecutor(null);
    }

    /**
     * Starts the server and waits for the OAuth callback.
     *
     * @param timeoutMs maximum time to wait for the callback
     * @return the authorization code
     * @throws UpstoxAuthException if the callback is not received within the timeout
     */
    public String waitForAuthorization(long timeoutMs) throws UpstoxAuthException {
        server.start();
        try {
            boolean received = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!received) {
                throw new UpstoxAuthException("Authorization timeout after " + timeoutMs + "ms");
            }
            if (error != null) {
                throw new UpstoxAuthException("Authorization error", error);
            }
            return authorizationCode;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstoxAuthException("Interrupted while waiting for authorization", e);
        } finally {
            server.stop(0);
        }
    }

    /** Returns the redirect URI for the authorization dialog. */
    public String redirectUri() {
        return "http://localhost:" + port + redirectPath;
    }

    /** The state parameter received (for CSRF validation). */
    public String state() {
        return state;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handleCallback(HttpExchange exchange) {
        try {
            String query = exchange.getRequestURI().getQuery();
            if (query == null) {
                sendResponse(exchange, 400, ERROR_RESPONSE);
                error = new UpstoxAuthException("No query parameters in redirect");
                latch.countDown();
                return;
            }
            String code = null;
            String errorParam = null;
            for (String param : query.split("&")) {
                String[] parts = param.split("=", 2);
                if (parts.length == 2) {
                    switch (parts[0]) {
                        case "code" -> code = java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                        case "state" -> state = java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                        case "error" -> errorParam = parts[1];
                        default -> { /* unknown parameter, ignore */ }
                    }
                }
            }
            if (errorParam != null) {
                sendResponse(exchange, 400, ERROR_RESPONSE);
                error = new UpstoxAuthException("Authorization error: " + errorParam);
                latch.countDown();
                return;
            }
            if (code == null) {
                sendResponse(exchange, 400, ERROR_RESPONSE);
                error = new UpstoxAuthException("No authorization code in redirect");
                latch.countDown();
                return;
            }
            authorizationCode = code;
            sendResponse(exchange, 200, SUCCESS_RESPONSE);
            latch.countDown();
        } catch (Exception e) {
            error = e;
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
