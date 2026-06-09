package com.tradej.core.domain.event;

public record EventCatalogEntry(
        String name,
        String simpleClassName,
        String packageName,
        String category,
        int schemaVersion
) {

    private static final String EVENT_PACKAGE = "com.tradej.core.domain.event";

    public static EventCatalogEntry fromClass(Class<?> eventClass) {
        String simpleName = eventClass.getSimpleName();
        String pkg = eventClass.getPackageName();
        return new EventCatalogEntry(
                simpleName,
                simpleName,
                pkg,
                categorize(simpleName),
                1
        );
    }

    static String categorize(String eventName) {
        if (eventName.contains("Tick") || eventName.contains("Depth") || eventName.contains("Candle")) {
            return "market";
        }
        if (eventName.contains("Order")) return "order";
        if (eventName.contains("Trade")) return "trade";
        if (eventName.contains("Signal")) return "signal";
        if (eventName.contains("Position") || eventName.contains("Pnl")) return "position";
        if (eventName.contains("Kill") || eventName.contains("Reconciliation") || eventName.contains("Suppressed")) {
            return "risk";
        }
        if (eventName.contains("Scan")) return "scan";
        if (eventName.contains("Option") || eventName.contains("Greeks") || eventName.contains("Gamma")
                || eventName.contains("MaxPain")) {
            return "analytics";
        }
        if (eventName.contains("Strategy") || eventName.contains("Error")) return "strategy";
        if (eventName.contains("Stream") || eventName.contains("Bus") || eventName.contains("Replay")) {
            return "infra";
        }
        if (eventName.contains("Execution")) return "execution";
        return "other";
    }
}
