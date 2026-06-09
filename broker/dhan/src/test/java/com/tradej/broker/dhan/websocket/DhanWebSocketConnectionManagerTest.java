package com.tradej.broker.dhan.websocket;

import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DhanWebSocketConnectionManager}.
 *
 * <p>Covers client lifecycle (creation, binding, closing), connection state
 * management, and accessor methods.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DhanWebSocketConnectionManagerTest {

    @Mock
    private DhanTokenProvider tokenProvider;

    private DhanConnectionSettings settings;
    private DhanWebSocketConnectionManager manager;

    @BeforeEach
    void setUp() {
        settings = DhanConnectionSettings.sandboxWithDefaults("test-client", "test-access-token");
        manager = new DhanWebSocketConnectionManager(settings, tokenProvider);
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    // ── Initial state ─────────────────────────────────────────────────

    @Nested
    class InitialStateTests {

        @Test
        void clientsAreNullAfterConstruction() {
            assertNull(manager.marketFeedClient(),
                    "Market feed client should be null after construction");
            assertNull(manager.orderStreamClient(),
                    "Order stream client should be null after construction");
        }

        @Test
        void isConnectedReturnsFalseInitially() {
            assertFalse(manager.isConnected(),
                    "isConnected should be false after construction");
        }
    }

    // ── Client creation ───────────────────────────────────────────────

    @Nested
    class ClientCreationTests {

        @Test
        void ensureClientsCreatesBothClients() {
            manager.ensureClients();

            assertNotNull(manager.marketFeedClient(),
                    "Market feed client should be created by ensureClients()");
            assertNotNull(manager.orderStreamClient(),
                    "Order stream client should be created by ensureClients()");
        }

        @Test
        void ensureClientsIsIdempotent() {
            manager.ensureClients();
            DhanMarketFeedWebSocketClient firstFeed = manager.marketFeedClient();
            DhanOrderStreamWebSocketClient firstOrder = manager.orderStreamClient();

            manager.ensureClients();

            assertSame(firstFeed, manager.marketFeedClient(),
                    "ensureClients should not replace existing clients");
            assertSame(firstOrder, manager.orderStreamClient(),
                    "ensureClients should not replace existing clients");
        }

        @Test
        void bindClientsCreatesFreshClients() {
            manager.ensureClients();
            DhanMarketFeedWebSocketClient originalFeed = manager.marketFeedClient();
            DhanOrderStreamWebSocketClient originalOrder = manager.orderStreamClient();

            manager.bindClients();

            assertNotNull(manager.marketFeedClient(),
                    "Market feed client should exist after bindClients()");
            assertNotNull(manager.orderStreamClient(),
                    "Order stream client should exist after bindClients()");
            assertNotSame(originalFeed, manager.marketFeedClient(),
                    "bindClients should create a new market feed client instance");
            assertNotSame(originalOrder, manager.orderStreamClient(),
                    "bindClients should create a new order stream client instance");
        }

        @Test
        void bindClientsWorksWhenClientsAreNull() {
            manager.bindClients();

            assertNotNull(manager.marketFeedClient(),
                    "bindClients should create clients even if they were null");
            assertNotNull(manager.orderStreamClient(),
                    "bindClients should create clients even if they were null");
        }
    }

    // ── Client lifecycle ──────────────────────────────────────────────

    @Nested
    class ClientLifecycleTests {

        @Test
        void closeCurrentClientsNullsBothClients() {
            manager.ensureClients();
            assertNotNull(manager.marketFeedClient());
            assertNotNull(manager.orderStreamClient());

            manager.closeCurrentClients();

            assertNull(manager.marketFeedClient(),
                    "Market feed client should be null after closeCurrentClients()");
            assertNull(manager.orderStreamClient(),
                    "Order stream client should be null after closeCurrentClients()");
        }

        @Test
        void closeCurrentClientsIsIdempotent() {
            manager.closeCurrentClients();
            // Should not throw
            manager.closeCurrentClients();
        }

        @Test
        void closeNullsBothClients() {
            manager.ensureClients();
            manager.close();

            assertNull(manager.marketFeedClient(),
                    "Market feed client should be null after close()");
            assertNull(manager.orderStreamClient(),
                    "Order stream client should be null after close()");
        }

        @Test
        void closeIsIdempotent() {
            manager.close();
            // Should not throw
            manager.close();
        }
    }

    // ── Connection state management ───────────────────────────────────

    @Nested
    class ConnectionStateTests {

        @Test
        void setConnectedMakesIsConnectedReturnTrue() {
            manager.setConnected(true);
            assertTrue(manager.isConnected());
        }

        @Test
        void setConnectedFalseMakesIsConnectedReturnFalse() {
            manager.setConnected(true);
            manager.setConnected(false);
            assertFalse(manager.isConnected());
        }

        @Test
        void disconnectAllSetsConnectedFalse() {
            manager.setConnected(true);
            manager.disconnectAll();
            assertFalse(manager.isConnected(),
                    "disconnectAll should set connected to false");
        }

        @Test
        void disconnectAllIsSafeWhenClientsAreNull() {
            // Clients are null after construction
            manager.disconnectAll();
            // Should not throw
        }

        @Test
        void disconnectAllIsSafeAfterClose() {
            manager.ensureClients();
            manager.close();

            manager.disconnectAll();
            // Should not throw even though clients were already closed
        }
    }

    // ── Connection in sandbox mode ────────────────────────────────────

    @Nested
    class SandboxConnectionTests {

        @Test
        void connectMarketFeedThrowsInSandbox() {
            manager.ensureClients();

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> manager.connectMarketFeed(),
                    "connectMarketFeed should throw in sandbox mode");
            assertTrue(ex.getMessage().contains("sandbox"),
                    "Error should mention sandbox mode");
        }

        @Test
        void connectOrderStreamThrowsInSandbox() {
            manager.ensureClients();

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> manager.connectOrderStream(),
                    "connectOrderStream should throw in sandbox mode");
            assertTrue(ex.getMessage().contains("sandbox"),
                    "Error should mention sandbox mode");
        }

        @Test
        void connectMarketFeedIsSafeWhenClientIsNull() {
            // No clients created — connect should be a no-op
            manager.connectMarketFeed();
            // Should not throw
        }

        @Test
        void connectOrderStreamIsSafeWhenClientIsNull() {
            // No clients created — connect should be a no-op
            manager.connectOrderStream();
            // Should not throw
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────

    @Nested
    class AccessorTests {

        @Test
        void marketFeedClientReturnsCurrentInstance() {
            manager.ensureClients();
            DhanMarketFeedWebSocketClient client = manager.marketFeedClient();

            assertNotNull(client);
            // Calling again should return the same instance
            assertSame(client, manager.marketFeedClient());
        }

        @Test
        void orderStreamClientReturnsCurrentInstance() {
            manager.ensureClients();
            DhanOrderStreamWebSocketClient client = manager.orderStreamClient();

            assertNotNull(client);
            // Calling again should return the same instance
            assertSame(client, manager.orderStreamClient());
        }
    }

    // ── Lifecycle integration ─────────────────────────────────────────

    @Nested
    class LifecycleIntegrationTests {

        @Test
        void fullLifecycleCreateCloseCreate() {
            // Create clients
            manager.ensureClients();
            assertNotNull(manager.marketFeedClient());

            // Close them
            manager.closeCurrentClients();
            assertNull(manager.marketFeedClient());

            // Create again
            manager.bindClients();
            assertNotNull(manager.marketFeedClient(),
                    "Should be able to create new clients after closing");
        }

        @Test
        void bindClientsClosesOldAndCreatesNew() {
            manager.ensureClients();
            DhanMarketFeedWebSocketClient original = manager.marketFeedClient();

            manager.bindClients();

            assertNotNull(manager.marketFeedClient());
            assertNotSame(original, manager.marketFeedClient(),
                    "bindClients should replace old clients with new instances");
        }
    }
}
