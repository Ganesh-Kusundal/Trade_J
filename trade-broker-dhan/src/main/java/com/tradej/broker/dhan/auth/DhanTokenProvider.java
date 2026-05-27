package com.tradej.broker.dhan.auth;

public interface DhanTokenProvider {
    String getAccessToken();

    DhanTokenInfo getTokenInfo();

    void ensureValid();
}
