package com.tradej.brokergateway.certification;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tradej.broker.api.spi.BrokerSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Persists and retrieves {@link CertificationArtifact} records to disk.
 *
 * <p>Artifacts are stored as JSON files under:
 * <pre>
 *   CertificationArtifacts/&lt;broker&gt;/&lt;category&gt;/&lt;check&gt;.json
 * </pre>
 *
 * <p>Secrets are sanitized before storage.
 */
public final class CertificationArtifactStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Path rootDir;

    public CertificationArtifactStore(Path rootDir) {
        this.rootDir = rootDir;
    }

    public CertificationArtifactStore() {
        this(Path.of("CertificationArtifacts"));
    }

    public void store(CertificationArtifact artifact) throws IOException {
        Path dir = rootDir
                .resolve(artifact.broker().name().toLowerCase())
                .resolve(artifact.category());
        Files.createDirectories(dir);
        Path file = dir.resolve(artifact.checkName() + ".json");
        MAPPER.writeValue(file.toFile(), artifact);
    }

    public void storeAll(BrokerSource broker, CertificationReport report) throws IOException {
        for (CertificationCheck check : report.checks()) {
            String category = categorize(check.name());
            CertificationArtifact artifact = CertificationArtifact.fromCheck(broker, check, category);
            store(artifact);
        }
    }

    public List<CertificationArtifact> loadAll(BrokerSource broker) throws IOException {
        Path brokerDir = rootDir.resolve(broker.name().toLowerCase());
        if (!Files.exists(brokerDir)) {
            return List.of();
        }
        List<CertificationArtifact> artifacts = new ArrayList<>();
        try (Stream<Path> files = Files.walk(brokerDir)) {
            files.filter(f -> f.toString().endsWith(".json"))
                    .forEach(f -> {
                        try {
                            artifacts.add(MAPPER.readValue(f.toFile(), CertificationArtifact.class));
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException("Failed to read artifact: " + f, e);
                        }
                    });
        }
        return artifacts;
    }

    public Optional<Instant> lastCertifiedAt(BrokerSource broker, String checkName) throws IOException {
        Path brokerDir = rootDir.resolve(broker.name().toLowerCase());
        if (!Files.exists(brokerDir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.walk(brokerDir)) {
            return files.filter(f -> f.getFileName().toString().equals(checkName + ".json"))
                    .findFirst()
                    .flatMap(f -> {
                        try {
                            CertificationArtifact a = MAPPER.readValue(f.toFile(), CertificationArtifact.class);
                            return Optional.of(a.certifiedAt());
                        } catch (IOException e) {
                            return Optional.empty();
                        }
                    });
        }
    }

    public int countArtifacts(BrokerSource broker) throws IOException {
        Path brokerDir = rootDir.resolve(broker.name().toLowerCase());
        if (!Files.exists(brokerDir)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(brokerDir)) {
            return (int) files.filter(f -> f.toString().endsWith(".json")).count();
        }
    }

    static String categorize(String checkName) {
        if (checkName.startsWith("ltp") || checkName.startsWith("quote") || checkName.startsWith("depth")
                || checkName.startsWith("ohlc") || checkName.startsWith("candles")) {
            return "market-data";
        }
        if (checkName.startsWith("option")) return "options";
        if (checkName.startsWith("portfolio") || checkName.startsWith("balance") || checkName.startsWith("holdings")) {
            return "portfolio";
        }
        if (checkName.startsWith("order") || checkName.startsWith("trade")) return "orders";
        if (checkName.startsWith("bracket") || checkName.startsWith("gtt") || checkName.startsWith("slice")) {
            return "advanced-orders";
        }
        if (checkName.startsWith("margin")) return "margin";
        if (checkName.startsWith("futures")) return "futures";
        if (checkName.startsWith("instrument")) return "catalog";
        if (checkName.startsWith("session") || checkName.startsWith("kill") || checkName.startsWith("news")
                || checkName.startsWith("websocket")) {
            return "capabilities";
        }
        return "other";
    }
}
