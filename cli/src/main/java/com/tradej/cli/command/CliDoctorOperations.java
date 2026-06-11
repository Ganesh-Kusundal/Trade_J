package com.tradej.cli.command;

import com.tradej.broker.api.spi.BrokerProvider;
import com.tradej.indicators.spi.IndicatorRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class CliDoctorOperations {

    private CliDoctorOperations() {}

    public record CheckResult(String name, String status, String detail) {}

    public static List<CheckResult> runAllChecks() {
        List<CheckResult> results = new ArrayList<>();

        results.add(checkJavaVersion());
        results.add(checkGradleWrapper());
        results.add(checkDuckDbDriver());
        results.add(checkBrokerProviders());
        results.add(checkIndicatorProviders());
        results.add(checkChroniclePaths());
        results.add(checkRuntimeDirs());

        return results;
    }

    static CheckResult checkJavaVersion() {
        String version = System.getProperty("java.version", "unknown");
        String vendor = System.getProperty("java.vendor", "unknown");
        boolean ok = isJavaVersionSupported(version);
        return new CheckResult(
                "Java Version",
                ok ? "PASS" : "WARN",
                version + " (" + vendor + ")"
        );
    }

    static CheckResult checkGradleWrapper() {
        File gradlew = new File("gradlew");
        boolean exists = gradlew.exists() && gradlew.canExecute();
        return new CheckResult(
                "Gradle Wrapper",
                exists ? "PASS" : "WARN",
                exists ? "gradlew found" : "gradlew not found or not executable"
        );
    }

    static CheckResult checkDuckDbDriver() {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
            return new CheckResult("DuckDB Driver", "PASS", "driver loaded");
        } catch (ClassNotFoundException e) {
            return new CheckResult("DuckDB Driver", "WARN", "driver not on classpath");
        }
    }

    static CheckResult checkBrokerProviders() {
        int count = 0;
        List<String> names = new ArrayList<>();
        for (BrokerProvider provider : ServiceLoader.load(BrokerProvider.class)) {
            count++;
            names.add(provider.displayName());
        }
        return new CheckResult(
                "Broker Providers",
                count > 0 ? "PASS" : "FAIL",
                count + " discovered: " + String.join(", ", names)
        );
    }

    static CheckResult checkIndicatorProviders() {
        IndicatorRegistry registry = IndicatorRegistry.discover();
        int count = registry.size();
        return new CheckResult(
                "Indicator Providers",
                count > 0 ? "PASS" : "WARN",
                count + " discovered: " + String.join(", ", registry.names())
        );
    }

    static CheckResult checkChroniclePaths() {
        File chronicleDir = new File("runtime-dev/chronicle");
        boolean exists = chronicleDir.exists();
        return new CheckResult(
                "Chronicle Queue",
                exists ? "PASS" : "INFO",
                exists ? "runtime-dev/chronicle exists" : "runtime-dev/chronicle not found (created on first run)"
        );
    }

    static CheckResult checkRuntimeDirs() {
        List<String> missing = new ArrayList<>();
        for (String dir : List.of("runtime-dev", "data")) {
            if (!new File(dir).exists()) {
                missing.add(dir);
            }
        }
        return new CheckResult(
                "Runtime Directories",
                missing.isEmpty() ? "PASS" : "INFO",
                missing.isEmpty() ? "all runtime directories present" : "missing: " + String.join(", ", missing)
        );
    }

    private static boolean isJavaVersionSupported(String version) {
        try {
            int major = Integer.parseInt(version.split("[^0-9]")[0]);
            return major >= 21;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
