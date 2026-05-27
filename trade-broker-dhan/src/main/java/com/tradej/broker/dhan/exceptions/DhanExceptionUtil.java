package com.tradej.broker.dhan.exceptions;

import com.tradej.broker.dhan.auth.DhanAuthenticationException;

import java.io.IOException;

/**
 * Shared utility for consistent error handling across Dhan broker components.
 *
 * <p>Consolidates patterns that were previously duplicated across
 * {@link com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient},
 * {@link com.tradej.broker.dhan.auth.DhanAuthClient}, and various adapters:
 * <ul>
 *   <li>HTTP response status code validation (401/403 → auth, !200 → HTTP)</li>
 *   <li>IO exception rethrowing with a consistent action description</li>
 *   <li>Interruption handling (restore flag, throw runtime exception)</li>
 * </ul>
 */
public final class DhanExceptionUtil {
    private DhanExceptionUtil() {
    }

    /**
     * Validates an HTTP response status code and throws the appropriate
     * Dhan exception on failure.
     *
     * @param statusCode HTTP status code
     * @param body       response body (included in the error message)
     * @param action     short description of the operation that was attempted
     * @throws DhanAuthenticationException on 401 or 403
     * @throws DhanHttpException           on any other non-200 status
     */
    public static void verifyHttpSuccess(int statusCode, String body, String action) {
        if (statusCode == 401 || statusCode == 403) {
            throw new DhanAuthenticationException(
                    "Dhan " + action + " failed: HTTP " + statusCode + " " + body);
        }
        if (statusCode != 200) {
            throw new DhanHttpException(
                    "Dhan " + action + " failed: HTTP " + statusCode + " " + body);
        }
    }

    /**
     * Rethrows an {@link IOException} as a {@link DhanHttpException} with a
     * consistent message format.
     */
    public static void rethrowIoError(String action, IOException ex) {
        throw new DhanHttpException("Dhan " + action + " failed: " + ex.getMessage(), ex);
    }

    /**
     * Handles an {@link InterruptedException}: restores the interrupt flag on
     * the current thread and throws an {@link DhanHttpException}.
     */
    public static void rethrowInterruption(String action, InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new DhanHttpException("Dhan " + action + " interrupted", ex);
    }
}
