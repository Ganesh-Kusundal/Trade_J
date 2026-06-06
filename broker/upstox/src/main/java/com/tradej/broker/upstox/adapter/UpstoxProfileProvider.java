package com.tradej.broker.upstox.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.rest.UpstoxProfileRestClient;

/**
 * Provider for Upstox user profile information.
 * <p>
 * Caches the profile response so multiple field accessors ({@link #getClientId()},
 * {@link #getEmail()}, etc.) make at most one HTTP call. Call {@link #invalidateCache()}
 * if the profile may have changed (e.g. after token refresh).
 */
public final class UpstoxProfileProvider {

    private final UpstoxProfileRestClient restClient;

    private volatile JsonNode cachedData;

    public UpstoxProfileProvider(UpstoxProfileRestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Returns the raw profile response from the API (always fetches fresh data).
     */
    public JsonNode getProfile() {
        JsonNode response = restClient.getProfile();
        cachedData = response.get("data");
        return response;
    }

    /**
     * Invalidates the cached profile, forcing the next field access to re-fetch.
     */
    public void invalidateCache() {
        cachedData = null;
    }

    /**
     * Returns the user's client/account ID from profile.
     */
    public String getClientId() {
        JsonNode data = profileData();
        if (data != null) {
            if (data.has("client_id")) return data.get("client_id").asText();
            if (data.has("user_id")) return data.get("user_id").asText();
            if (data.has("email")) return data.get("email").asText();
        }
        return "";
    }

    /**
     * Returns the user's email from profile.
     */
    public String getEmail() {
        JsonNode data = profileData();
        if (data != null && data.has("email")) {
            return data.get("email").asText();
        }
        return "";
    }

    /**
     * Returns the user's name from profile.
     */
    public String getUserName() {
        JsonNode data = profileData();
        if (data != null && data.has("user_name")) {
            return data.get("user_name").asText();
        }
        return "";
    }

    /**
     * Returns the user's mobile number from profile.
     */
    public String getMobile() {
        JsonNode data = profileData();
        if (data != null && data.has("mobile")) {
            return data.get("mobile").asText();
        }
        return "";
    }

    /**
     * Returns cached profile data, fetching from the API on first access.
     */
    private JsonNode profileData() {
        JsonNode data = cachedData;
        if (data == null) {
            JsonNode response = restClient.getProfile();
            data = response.get("data");
            cachedData = data;
        }
        return data;
    }
}
