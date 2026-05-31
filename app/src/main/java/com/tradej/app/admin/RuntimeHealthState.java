package com.tradej.app.admin;

public final class RuntimeHealthState {
    private volatile boolean catalogLoaded;
    private volatile int catalogSize;
    private volatile boolean brokerPreflightPassed;
    private volatile boolean startupCompleted;

    public void markCatalogLoaded(int size) {
        this.catalogLoaded = true;
        this.catalogSize = size;
    }

    public void markBrokerPreflightPassed() {
        this.brokerPreflightPassed = true;
    }

    public void markStartupCompleted() {
        this.startupCompleted = true;
    }

    public boolean catalogLoaded() {
        return catalogLoaded;
    }

    public int catalogSize() {
        return catalogSize;
    }

    public boolean brokerPreflightPassed() {
        return brokerPreflightPassed;
    }

    public boolean startupCompleted() {
        return startupCompleted;
    }
}
