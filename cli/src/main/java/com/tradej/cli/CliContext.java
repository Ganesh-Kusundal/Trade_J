package com.tradej.cli;

import com.tradej.cli.attach.AttachClient;
import com.tradej.cli.config.CliConfig;
import com.tradej.cli.standalone.BrokerSession;
import com.tradej.cli.standalone.BrokerSessionFactory;
import com.tradej.brokergateway.BrokerGateway;
import com.tradej.brokergateway.BrokerHandle;
import com.tradej.broker.api.spi.BrokerSource;

public final class CliContext {
    private String attachUrl;
    private CliConfig.Profile profile;
    private CliConfig.BrokerType brokerType;
    private final boolean json;
    private final boolean yes;

    private AttachClient attachClient;
    private BrokerSession brokerSession;
    private BrokerGateway gateway;

    public CliContext(
            String attachUrl,
            CliConfig.Profile profile,
            CliConfig.BrokerType brokerType,
            boolean json,
            boolean yes
    ) {
        this.attachUrl = attachUrl;
        this.profile = profile;
        this.brokerType = brokerType;
        this.json = json;
        this.yes = yes;
    }

    public String attachUrl() {
        return attachUrl;
    }

    public CliConfig.Profile profile() {
        return profile;
    }

    public CliConfig.BrokerType brokerType() {
        return brokerType;
    }

    public boolean json() {
        return json;
    }

    public boolean yes() {
        return yes;
    }

    public void setAttachUrl(String attachUrl) {
        this.attachUrl = attachUrl.endsWith("/")
                ? attachUrl.substring(0, attachUrl.length() - 1)
                : attachUrl;
        this.attachClient = null;
    }

    public void setProfile(CliConfig.Profile profile) {
        this.profile = profile;
        resetBrokerSession();
    }

    public void setBrokerType(CliConfig.BrokerType brokerType) {
        this.brokerType = brokerType;
        resetBrokerSession();
    }

    public AttachClient attach() {
        if (attachClient == null) {
            attachClient = new AttachClient(attachUrl);
        }
        return attachClient;
    }

    public boolean attachReachable() {
        return attach().isReachable();
    }

    public BrokerSession broker() {
        if (brokerSession == null) {
            brokerSession = BrokerSessionFactory.create(brokerType, profile);
        }
        return brokerSession;
    }

    private void resetBrokerSession() {
        if (brokerSession != null) {
            brokerSession.close();
            brokerSession = null;
        }
    }

    public BrokerGateway gateway() {
        if (gateway == null) {
            BrokerSource source = toSource(brokerType);
            broker().ensureCatalogLoaded();
            gateway = BrokerGateway.of(source, broker().fullComposition().brokerConnection());
        }
        return gateway;
    }

    public BrokerHandle brokerHandle() {
        return gateway().first();
    }

    public BrokerHandle brokerHandle(String name) {
        return gateway().broker(name);
    }

    private static BrokerSource toSource(CliConfig.BrokerType type) {
        return switch (type) {
            case DHAN -> BrokerSource.DHAN;
            case UPSTOX -> BrokerSource.UPSTOX;
            case ICICI -> BrokerSource.ICICI;
        };
    }

    public void close() {
        resetBrokerSession();
    }
}
