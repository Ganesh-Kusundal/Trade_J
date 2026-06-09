package com.tradej.broker.dhan.reactive.adapter;

import com.tradej.broker.dhan.reactive.client.DhanReactiveHttpClient;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentResolver;
import com.tradej.broker.dhan.reactive.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * TDD Tests for DhanReactiveMarketDataProvider
 * 
 * RED Phase: These tests define the expected behavior of reactive market data provider.
 * They should fail initially, then we'll implement the code to make them pass.
 */
@ExtendWith(MockitoExtension.class)
class DhanReactiveMarketDataProviderTest {
    
    @Mock
    private DhanReactiveHttpClient mockHttpClient;
    
    @Mock
    private DhanInstrumentResolver mockResolver;
    
    private DhanReactiveMarketDataProvider provider;
    
    @BeforeEach
    void setUp() {
        provider = new DhanReactiveMarketDataProvider(mockHttpClient, mockResolver);
    }
    
    @Test
    void getLtpPaisaShouldReturnPrice() {
        // Arrange
        InstrumentKey key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        when(mockResolver.resolve(key))
            .thenReturn(new DhanInstrumentDefinition("NSE_EQ", "2885", "RELIANCE"));
        
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.just(new DhanJsonResponse("{\"last_price\": \"2456.70\"}")));
        
        // Act & Assert
        StepVerifier.create(provider.getLtpPaisa(key))
            .expectNext(245670L) // In paise (cents)
            .verifyComplete();
    }
    
    @Test
    void getQuoteShouldReturnQuote() {
        // Arrange
        InstrumentKey key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        when(mockResolver.resolve(key))
            .thenReturn(new DhanInstrumentDefinition("NSE_EQ", "2885", "RELIANCE"));
        
        String quoteJson = """
            {
                "last_price": "2456.70",
                "open": "2440.00",
                "high": "2470.00",
                "low": "2435.00",
                "close": "2450.00",
                "volume": "1000000"
            }
            """;
        
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.just(new DhanJsonResponse(quoteJson)));
        
        // Act & Assert
        StepVerifier.create(provider.getQuote(key))
            .expectNextMatches(quote -> 
                quote.ltpPaisa() == 245670 &&
                quote.openPaisa() == 244000 &&
                quote.highPaisa() == 247000 &&
                quote.lowPaisa() == 243500
            )
            .verifyComplete();
    }
    
    @Test
    void getCandlesShouldReturnCandles() {
        // Arrange
        CandleHistoryRequest request = new CandleHistoryRequest(
            new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ),
            "1m",
            java.time.LocalDate.now().minusDays(1),
            java.time.LocalDate.now()
        );
        
        when(mockResolver.resolve(request.instrument()))
            .thenReturn(new DhanInstrumentDefinition("NSE_EQ", "2885", "RELIANCE"));
        
        String candlesJson = """
            [
                {
                    "open": "2440.00",
                    "high": "2445.00",
                    "low": "2438.00",
                    "close": "2442.00",
                    "volume": "1000"
                },
                {
                    "open": "2442.00",
                    "high": "2448.00",
                    "low": "2441.00",
                    "close": "2446.00",
                    "volume": "1200"
                }
            ]
            """;
        
        when(mockHttpClient.getJsonStream(anyString()))
            .thenReturn(Flux.just(new DhanJsonResponse(candlesJson)));
        
        // Act & Assert
        StepVerifier.create(provider.getCandles(request))
            .expectNextCount(2)
            .verifyComplete();
    }
    
    @Test
    void shouldHandleApiError() {
        // Arrange
        InstrumentKey key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        when(mockResolver.resolve(key))
            .thenReturn(new DhanInstrumentDefinition("NSE_EQ", "2885", "RELIANCE"));
        
        when(mockHttpClient.getJson(anyString()))
            .thenReturn(Mono.error(new RuntimeException("API error")));
        
        // Act & Assert
        StepVerifier.create(provider.getLtpPaisa(key))
            .expectErrorMessage("API error")
            .verify();
    }
    
    @Test
    void getLtpBatchShouldReturnMultiplePrices() {
        // Arrange
        InstrumentKey key1 = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        InstrumentKey key2 = new InstrumentKey("TCS", ExchangeSegment.NSE_EQ);
        
        when(mockResolver.resolve(key1))
            .thenReturn(new DhanInstrumentDefinition("NSE_EQ", "2885", "RELIANCE"));
        when(mockResolver.resolve(key2))
            .thenReturn(new DhanInstrumentDefinition("NSE_EQ", "3647", "TCS"));
        
        String batchResponse = """
            {
                "NSE_EQ": {
                    "2885": {"last_price": "2456.70"},
                    "3647": {"last_price": "3890.50"}
                }
            }
            """;
        
        when(mockHttpClient.postJson(anyString(), any()))
            .thenReturn(Mono.just(new DhanJsonResponse(batchResponse)));
        
        // Act & Assert
        StepVerifier.create(provider.getLtpBatch(List.of(key1, key2)))
            .expectNextMatches(map -> 
                map.size() == 2 &&
                map.get(key1) == 245670L &&
                map.get(key2) == 389050L
            )
            .verifyComplete();
    }
    
    @Test
    void shouldHandleEmptyInstrumentList() {
        // Act & Assert
        StepVerifier.create(provider.getLtpBatch(List.of()))
            .expectNext(Map.of())
            .verifyComplete();
        
        // Verify no HTTP call was made
        org.mockito.Mockito.verifyNoInteractions(mockHttpClient);
    }
}
