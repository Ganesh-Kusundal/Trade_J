package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.core.domain.event.EventCatalogEntry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliEventsCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void eventsCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("events"),
                "events command should be registered");
    }

    @Test
    void eventsCommand_hasFilterOption() {
        var eventsCmd = cmd.getSubcommands().get("events");
        assertNotNull(eventsCmd);
        var filterOption = eventsCmd.getCommandSpec().optionsMap().get("--filter");
        assertNotNull(filterOption, "--filter option should exist");
    }

    @Test
    void eventsCommand_hasGroupOption() {
        var eventsCmd = cmd.getSubcommands().get("events");
        assertNotNull(eventsCmd);
        var groupOption = eventsCmd.getCommandSpec().optionsMap().get("--group");
        assertNotNull(groupOption, "--group option should exist");
    }

    @Test
    void discoverEvents_returnsAllDomainEvents() {
        List<EventCatalogEntry> events = CliEventsCommand.discoverEvents();
        assertTrue(events.size() >= 30, "should discover at least 30 domain events, found: " + events.size());

        List<String> names = events.stream().map(EventCatalogEntry::name).toList();
        assertTrue(names.contains("MarketTickEvent"), "MarketTickEvent should be in catalog");
        assertTrue(names.contains("OrderAccepted"), "OrderAccepted should be in catalog");
        assertTrue(names.contains("SignalGenerated"), "SignalGenerated should be in catalog");
        assertTrue(names.contains("KillSwitchEngaged"), "KillSwitchEngaged should be in catalog");
    }

    @Test
    void eventCategories_areAssigned() {
        List<EventCatalogEntry> events = CliEventsCommand.discoverEvents();
        var categories = events.stream().map(EventCatalogEntry::category).distinct().toList();
        assertTrue(categories.contains("market"), "should have market category");
        assertTrue(categories.contains("order"), "should have order category");
        assertTrue(categories.contains("signal"), "should have signal category");
    }
}
