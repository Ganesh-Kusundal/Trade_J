package com.tradej.broker.icici.auth;

public interface BreezeTokenProvider {
    void ensureValid();

    BreezeSession session();

    String appKey();

    String secretKey();
}
