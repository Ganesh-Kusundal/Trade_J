package com.tradej.broker.dhan.client;

import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import io.github.sonicalgo.dhan.Dhan;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class DhanClientHolder implements AutoCloseable {
    private final DhanConnectionSettings settings;
    private final DhanTokenProvider tokenProvider;
    private final CopyOnWriteArrayList<Consumer<Dhan>> rotationListeners = new CopyOnWriteArrayList<>();

    private volatile Dhan currentClient;
    private volatile String currentToken;

    public DhanClientHolder(DhanConnectionSettings settings, DhanTokenProvider tokenProvider) {
        this.settings = settings;
        this.tokenProvider = tokenProvider;
    }

    public Dhan client() {
        if (settings.isSandbox()) {
            throw new IllegalStateException("Dhan SDK client is unavailable in sandbox mode. Use REST adapters.");
        }
        Dhan clientToClose = null;
        Dhan rebuiltClient = null;
        Dhan result;
        String token = tokenProvider.getAccessToken();
        List<Consumer<Dhan>> listeners = List.of();
        synchronized (this) {
            if (currentClient == null || !Objects.equals(currentToken, token)) {
                clientToClose = currentClient;
                currentClient = buildClient(token);
                currentToken = token;
                rebuiltClient = currentClient;
                listeners = List.copyOf(rotationListeners);
            }
            result = currentClient;
        }
        if (clientToClose != null) {
            clientToClose.close();
        }
        if (rebuiltClient != null && clientToClose != null) {
            for (Consumer<Dhan> listener : listeners) {
                listener.accept(rebuiltClient);
            }
        }
        return result;
    }

    public void addRotationListener(Consumer<Dhan> listener) {
        rotationListeners.add(listener);
    }

    @Override
    public void close() {
        Dhan clientToClose;
        synchronized (this) {
            clientToClose = currentClient;
            currentClient = null;
            currentToken = null;
        }
        if (clientToClose != null) {
            clientToClose.close();
        }
    }

    private Dhan buildClient(String token) {
        return Dhan.builder()
                .clientId(settings.clientId())
                .accessToken(token)
                .loggingEnabled(settings.loggingEnabled())
                .rateLimitRetries(settings.rateLimitRetries())
                .build();
    }
}
