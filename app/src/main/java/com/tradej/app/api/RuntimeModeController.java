package com.tradej.app.api;

import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * REST endpoint that exposes the process-wide {@link RuntimeModeHolder} so the
 * UI can read the current runtime mode and switch between LIVE and PAPER
 * without a server restart.
 *
 * <p>Closes the audit's Deliverable 12 gap #8 ("no first-class 'live' mode
 * toggle in the UI"). REPLAY and BACKTEST are dev/test-only modes and are
 * rejected with {@code 400} — they must be set via Spring profile, not the UI.
 *
 * <p>Requires a valid JWT (G1 wiring on {@code /api/v1/**}).
 */
@RestController
@RequestMapping("/api/v1/runtime")
public class RuntimeModeController {

    private final RuntimeModeHolder holder;

    public RuntimeModeController(RuntimeModeHolder holder) {
        this.holder = holder;
    }

    @GetMapping("/mode")
    public ResponseEntity<Map<String, Object>> getMode() {
        return ResponseEntity.ok(Map.of(
                "mode", holder.mode().name(),
                "userToggleable", holder.mode().isUserToggleable()
        ));
    }

    @PutMapping("/mode")
    public ResponseEntity<Map<String, Object>> putMode(@RequestBody ModeRequest body) {
        if (body == null || body.mode() == null || body.mode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode is required");
        }
        RuntimeMode requested;
        try {
            requested = RuntimeMode.valueOf(body.mode().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid mode '" + body.mode() + "'. Allowed: LIVE, PAPER");
        }
        if (!requested.isUserToggleable()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mode '" + requested.name()
                            + "' is not user-toggleable. Allowed: LIVE, PAPER");
        }
        holder.setMode(requested);
        return ResponseEntity.ok(Map.of(
                "mode", holder.mode().name(),
                "userToggleable", holder.mode().isUserToggleable()
        ));
    }

    public record ModeRequest(String mode) {}
}
