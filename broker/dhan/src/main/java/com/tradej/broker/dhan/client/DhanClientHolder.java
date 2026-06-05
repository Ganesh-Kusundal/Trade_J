package com.tradej.broker.dhan.client;

import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConnectionSettings;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds Dhan connection credentials and notifies listeners when the access token rotates.
 *
 * <p>REST and native WebSocket transports read tokens via {@link DhanTokenProvider}; this holder
 * exists for token-rotation callbacks (e.g. reconnecting live feeds after TOTP refresh).
 */
public final class DhanClientHolder implements AutoCloseable {
  private final DhanConnectionSettings settings;
  private final DhanTokenProvider tokenProvider;
  private final CopyOnWriteArrayList<Runnable> rotationListeners = new CopyOnWriteArrayList<>();

  private volatile String currentToken;

  public DhanClientHolder(DhanConnectionSettings settings, DhanTokenProvider tokenProvider) {
    this.settings = settings;
    this.tokenProvider = tokenProvider;
  }

  public DhanConnectionSettings settings() {
    return settings;
  }

  public DhanTokenProvider tokenProvider() {
    return tokenProvider;
  }

  public String accessToken() {
    String token = tokenProvider.getAccessToken();
    Runnable[] listeners = null;
    synchronized (this) {
      if (!Objects.equals(currentToken, token)) {
        currentToken = token;
        listeners = rotationListeners.toArray(Runnable[]::new);
      }
    }
    if (listeners != null) {
      for (Runnable listener : listeners) {
        listener.run();
      }
    }
    return token;
  }

  public void ensureValidToken() {
    tokenProvider.ensureValid();
    accessToken();
  }

  public void addRotationListener(Runnable listener) {
    rotationListeners.add(listener);
  }

  @Override
  public void close() {
    synchronized (this) {
      currentToken = null;
    }
  }
}
