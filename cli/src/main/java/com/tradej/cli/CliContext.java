package com.tradej.cli;

import com.tradej.cli.attach.AttachClient;
import com.tradej.cli.config.CliConfig;
import com.tradej.cli.standalone.BrokerSession;
import com.tradej.cli.standalone.BrokerSessionFactory;
import com.tradej.brokergateway.BrokerGateway;
import com.tradej.brokergateway.BrokerHandle;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.spi.BrokerDescriptor;
import com.tradej.brokergateway.spi.BrokerRegistry;
import com.tradej.brokergateway.spi.ServiceLoaderBrokerRegistry;
import com.tradej.composition.config.BrokerProfile;

public final class CliContext {
    private String attachUrl;
    private CliConfig.Profile profile;
    private CliConfig.BrokerType brokerType;
    private final boolean json;
    private final boolean yes;

    private AttachClient attachClient;
    private BrokerSession brokerSession;
    private BrokerGateway gateway;
    private ServiceLoaderBrokerRegistry registry;

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
            gateway = BrokerGateway.of(source, broker().connection());
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
        };
    }

    /**
     * Returns the broker registry, auto-discovering providers via ServiceLoader.
     */
    public BrokerRegistry registry() {
        if (registry == null) {
            registry = new ServiceLoaderBrokerRegistry();
        }
        return registry;
    }

    /**
     * Returns descriptors for all available brokers.
     */
    public java.util.List<BrokerDescriptor> availableBrokers() {
        return registry().descriptors();
    }

    /**
     * Creates a BrokerGateway using the registry and a broker profile.
     * This is the preferred way to create a gateway — it uses SPI-discovered providers.
     */
    public BrokerGateway gatewayFromRegistry(BrokerProfile profile) {
        return BrokerGateway.fromRegistry(registry(), profile);
    }

    public void close() {
        resetBrokerSession();
    }
}
