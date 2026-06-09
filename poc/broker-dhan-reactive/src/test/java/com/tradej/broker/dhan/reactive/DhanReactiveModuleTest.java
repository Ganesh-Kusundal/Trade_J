package com.tradej.broker.dhan.reactive;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Initial module validation test.
 * 
 * RED: This test verifies that reactive dependencies are properly configured.
 * It should pass once the build.gradle is set up correctly.
 */
class DhanReactiveModuleTest {
    
    @Test
    void moduleShouldLoadReactiveDependencies() {
        // Verify Project Reactor is available
        assertDoesNotThrow(() -> Mono.just("test"));
        assertDoesNotThrow(() -> Flux.just("test"));
        
        // Verify WebClient is available
        assertDoesNotThrow(() -> WebClient.create());
    }
    
    @Test
    void moduleShouldHaveCorrectPackageStructure() {
        // Verify the reactive package exists
        String packageName = DhanReactiveModuleTest.class.getPackageName();
        assert(packageName.equals("com.tradej.broker.dhan.reactive")) 
            : "Expected package com.tradej.broker.dhan.reactive but got " + packageName;
    }
}
