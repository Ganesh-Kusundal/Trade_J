package com.tradej.app.scanner;

import com.tradej.scanner.model.ScanProfile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ScanProfileCatalog {

    private final Map<String, ScanProfile> profiles = new ConcurrentHashMap<>();
    private final List<Consumer<ScanProfile>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void register(ScanProfile profile) {
        profiles.put(profile.id(), profile);
        notifyListeners(profile);
    }

    public void unregister(String profileId) {
        profiles.remove(profileId);
    }

    public ScanProfile get(String profileId) {
        return profiles.get(profileId);
    }

    public List<ScanProfile> getAll() {
        return List.copyOf(profiles.values());
    }

    public boolean exists(String profileId) {
        return profiles.containsKey(profileId);
    }

    public void update(ScanProfile profile) {
        if (!profiles.containsKey(profile.id())) {
            throw new IllegalArgumentException("Profile not found: " + profile.id());
        }
        profiles.put(profile.id(), profile);
        notifyListeners(profile);
    }

    public void subscribe(Consumer<ScanProfile> listener) {
        listeners.add(listener);
    }

    private void notifyListeners(ScanProfile profile) {
        for (Consumer<ScanProfile> listener : listeners) {
            listener.accept(profile);
        }
    }
}