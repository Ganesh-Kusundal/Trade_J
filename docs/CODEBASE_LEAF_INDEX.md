# Trade-J — Leaf File Index (generated from repo)

> **Audit date:** auto-generated on write  
> **Totals:** 654 main Java · 212 test Java (+ 1 architecture-test)  
> **Canonical architecture:** [ARCHITECTURE_REPORT.md](ARCHITECTURE_REPORT.md)

Per-module listing of every `src/main/java` compilation unit, grouped by package.

Regenerate:

```bash
python3 scripts/generate-codebase-leaf-index.py
```


### `:core` — `core/` (159 main · 14 test)
- **`com/tradej/core/domain`** (1): SequenceService
- **`com/tradej/core/domain/event`** (36): BrokerAdapterError, CandleClosed, CandleDeveloping, DepthUpdateEvent, DomainEvent, EventBusBackpressure, EventMetadata, EventMetadataFactory, EventPriority, EventSchemaVersion, KillSwitchEngaged, MarketTickEvent, OrderAccepted, OrderCancelled, OrderFilled, OrderFullyFilled, OrderModified, OrderPartiallyFilled, OrderRejected, OrderUpdateEvent, PnlUpdatedEvent, PositionMismatch, PositionUpdateEvent, ReplayTimeChangedEvent, ScanHitProduced, ScanResultsPublished, SignalGenerated, SignalPendingExecution, SignalSuppressed, StrategyError, StreamHealthChanged, TickReceived, TradeClosed, TradeExecutionEvent, TradeOpened, TradeUpdated
- **`com/tradej/core/domain/instrument`** (7): ContractSymbolMatcher, ContractSymbolNormalizer, ExpiredOptionContractKey, RollingExpiryKind, RollingExpiryRoll, RollingOptionSeriesKey, StrikeOffset
- **`com/tradej/core/domain/market`** (2): CandleBucketPolicy, CandleIntervalSpec
- **`com/tradej/core/domain/model`** (39): AnalyticsCatalogSnapshot, AnalyticsQueryResult, Balance, Candle, CandleHistoryRequest, ConditionalAlert, ConditionalAlertRequest, DepthLevel, ExpiredOptionBar, FeatureGenerator, FeatureVector, Holding, InferenceResult, Instrument, InstrumentKey, LivePnlSnapshot, MarginEstimate, MarginEstimateRequest, MarketDepth, ModifyOrderRequest, OptionChainEntry, OptionChainSnapshot, OptionGreeks, OptionQuote, OptionStrikeSelection, Order, OrderRequest, PnlExitPolicy, PnlExitResult, Position, Quote, RiskLimits, RollingOptionBar, RollingOptionHistoryRequest, RollingOptionSeries, RollingOptionSeriesRequest, SliceOrderRequest, Trade, UniverseEntry
- **`com/tradej/core/domain/oms`** (13): CancelRequested, LifecycleState, OrderAcknowledged, OrderCancelled, OrderEvent, OrderExpired, OrderFullyFilled, OrderPartiallyFilled, OrderProjection, OrderRejected, OrderStateMachine, OrderSubmitted, package-info
- **`com/tradej/core/domain/port`** (11): DeadLetterQueue, DomainEventHandler, EventBus, FeatureStore, HistoricalAnalyticsService, HistoricalBarRepository, MLInferenceEngine, ModelRegistry, NetPositionProvider, PositionSizer, RollingOptionHistoricalRepository
- **`com/tradej/core/domain/runtime`** (2): RuntimeMode, RuntimeModeHolder
- **`com/tradej/core/domain/value`** (14): AssetClass, Exchange, ExchangeSegment, FeedMode, FillReconciliation, OptionType, OrderStatus, OrderType, PriceMath, ProductType, SessionSchedule, Side, StrikeSelectionKind, Validity
- **`com/tradej/core/infrastructure`** (1): WorkspacePaths
- **`com/tradej/core/routing`** (1): SymbolShardRouter
- **`com/tradej/core/support`** (1): MdcHelper
- **`com/tradej/pipeline/clock`** (2): EventTimestamps, VirtualClock
- **`com/tradej/pipeline/compiler`** (2): CompilationResult, GraphNormalizer
- **`com/tradej/pipeline/graph`** (6): IngressNodeConfig, PipelineEdgeDef, PipelineExecutionMode, PipelineGraph, PipelineGraphValidator, PipelineNodeDef
- **`com/tradej/pipeline/reactor`** (1): ReactorBridge
- **`com/tradej/pipeline/registry`** (2): NodeRegistry, NodeTypeDescriptor
- **`com/tradej/pipeline/runtime`** (15): BacktestFillModel, BasePipelineNode, ExecutionPlan, GraphCompiler, GraphRuntime, IngressNode, NodeMetrics, NodeState, PartitionedNode, PipelineContext, PipelineNode, PipelineNodeTypes, PipelineRuntime, PipelineRuntimeBridge, ReactivePipelineNode
- **`com/tradej/pipeline/state`** (3): InMemoryStateStore, StateScope, StateStore
- **tests:** com/tradej/core/architecture/ModuleDependencyTest, com/tradej/core/domain/instrument/ContractSymbolNormalizerTest, com/tradej/core/domain/instrument/RollingOptionSeriesKeyTest, com/tradej/core/domain/instrument/StrikeOffsetTest, com/tradej/core/domain/market/CandleBucketPolicyTest, com/tradej/core/domain/model/FeatureGeneratorTest, com/tradej/core/domain/oms/OrderStateMachineUnitTest, com/tradej/core/domain/value/PriceMathUnitTest, com/tradej/core/infrastructure/WorkspacePathsTest, com/tradej/pipeline/graph/IngressNodeConfigTest, com/tradej/pipeline/graph/PipelineGraphValidatorTest, com/tradej/pipeline/reactor/ReactorBridgeTest, com/tradej/pipeline/runtime/DagGraphRuntimeTest, com/tradej/pipeline/runtime/GraphRuntimeTest

### `:broker-api` — `broker/api/` (28 main · 0 test)
- **`com/tradej/broker/api`** (1): IBrokerConnection
- **`com/tradej/broker/api/auth`** (3): TokenLifecycleService, TokenSource, TokenState
- **`com/tradej/broker/api/model`** (5): BrokerCapabilities, BrokerTransportCapabilities, MarketSessionPolicy, MarketSubscriptionRequest, VenueCapability
- **`com/tradej/broker/api/port`** (17): BracketOrderProvider, ConditionalAlertProvider, FuturesProvider, GttOrderProvider, IdempotencyCachePort, InstrumentResolver, MarginProvider, MarketDataListener, MarketDataProvider, OptionsProvider, OrderCommand, OrderQuery, OrderUpdateListener, PortfolioProvider, SessionRiskProvider, SliceOrderCommand, WebSocketMultiplexer
- **`com/tradej/broker/api/resilience`** (1): BrokerErrorCategory
- **`com/tradej/broker/api/websocket`** (1): WebSocketSupervisor

### `:broker-core` — `broker/core/` (14 main · 0 test)
- **`com/tradej/broker/core/auth`** (3): DefaultTokenLifecycleService, JsonTokenStateStore, TokenStateStore
- **`com/tradej/broker/core/observability`** (2): ObservableMarketDataProvider, ObservableOrderCommand
- **`com/tradej/broker/core/rate`** (3): MultiBucketRateLimiter, RateLimitConfig, TokenBucketRateLimiter
- **`com/tradej/broker/core/resilience`** (4): BackoffStrategy, CircuitBreaker, RetryExecutor, RetryPolicy
- **`com/tradej/broker/core/util`** (1): ReflectionSupport
- **`com/tradej/broker/core/websocket`** (1): DefaultWebSocketSupervisor

### `:broker-dhan` — `broker/dhan/` (69 main · 19 test)
- **`com/tradej/broker/dhan`** (1): DhanBrokerConnection
- **`com/tradej/broker/dhan/adapter`** (15): DhanBaseRestAdapter, DhanBracketOrderAdapter, DhanConditionalAlertProvider, DhanFuturesAdapter, DhanGttOrderAdapter, DhanInstrumentResolver, DhanMarginProvider, DhanMarketDataProvider, DhanOptionsAdapter, DhanOrderCommandAdapter, DhanOrderQueryAdapter, DhanPortfolioProvider, DhanSessionRiskProvider, DhanSliceOrderAdapter, InMemoryInstrumentResolver
- **`com/tradej/broker/dhan/auth`** (9): DhanAuthClient, DhanAuthRejectedException, DhanAuthenticationException, DhanTokenInfo, DhanTokenManager, DhanTokenProvider, DhanTokenState, DhanTokenStateStore, DhanTotpGenerator
- **`com/tradej/broker/dhan/client`** (1): DhanClientHolder
- **`com/tradej/broker/dhan/config`** (6): DhanApiEnvironment, DhanAuthMode, DhanBrokerCapabilities, DhanBrokerStartup, DhanConfigPaths, DhanConnectionSettings
- **`com/tradej/broker/dhan/constants`** (3): DhanApiEndpoints, DhanApiUrlResolver, DhanProtocolConstants
- **`com/tradej/broker/dhan/exceptions`** (3): DhanBrokerException, DhanExceptionUtil, DhanHttpException
- **`com/tradej/broker/dhan/historical`** (2): DhanHistoricalDataClient, DhanHistoricalDataMapper
- **`com/tradej/broker/dhan/http`** (1): DhanAuthenticatedHttpClient
- **`com/tradej/broker/dhan/instrument`** (5): DhanInstrumentCatalog, DhanInstrumentDefinition, DhanInstrumentLoader, DhanSegmentMapper, DhanSymbolNormalizer
- **`com/tradej/broker/dhan/mapper`** (6): DhanJsonResponse, DhanPayloadNormalizer, DhanSdkConverters, DhanSdkMapper, DhanSdkResponse, ReflectionSupport
- **`com/tradej/broker/dhan/options`** (7): DhanOptionChainClient, DhanOptionChainResponseMapper, DhanRollingOptionClient, DhanRollingOptionMapper, DhanRollingOptionWireMapper, OptionExpiryCache, StrikeSelectionSupport
- **`com/tradej/broker/dhan/orders`** (1): DhanRestOrderClient
- **`com/tradej/broker/dhan/rate`** (4): ApiCategory, DhanEndpointCategory, MultiBucketRateLimiter, TokenBucketRateLimiter
- **`com/tradej/broker/dhan/resilience`** (2): DhanBackoffUtil, DhanResilienceExecutor
- **`com/tradej/broker/dhan/websocket`** (3): DhanBinaryParser, DhanWebSocketMultiplexer, ParsedFeedFrame
- **tests:** com/tradej/broker/dhan/adapter/DhanMarketDataProviderMergeTest, com/tradej/broker/dhan/adapter/InMemoryInstrumentResolverContractTest, com/tradej/broker/dhan/auth/DhanAuthClientUnitTest, com/tradej/broker/dhan/auth/DhanTokenManagerUnitTest, com/tradej/broker/dhan/auth/DhanTotpGeneratorUnitTest, com/tradej/broker/dhan/config/DhanConfigPathsUnitTest, com/tradej/broker/dhan/config/DhanConnectionSettingsUnitTest, com/tradej/broker/dhan/constants/DhanApiUrlResolverUnitTest, com/tradej/broker/dhan/historical/DhanHistoricalDataClientFailureTest, com/tradej/broker/dhan/historical/DhanHistoricalDataClientWindowingTest, com/tradej/broker/dhan/instrument/DhanInstrumentCatalogComponentTest, com/tradej/broker/dhan/instrument/DhanInstrumentCatalogContractTest, com/tradej/broker/dhan/options/DhanOptionChainResponseMapperTest, com/tradej/broker/dhan/options/DhanRollingOptionMapperTest, com/tradej/broker/dhan/options/DhanRollingOptionWireMapperTest, com/tradej/broker/dhan/options/OptionExpiryCacheTest, com/tradej/broker/dhan/options/StrikeSelectionSupportTest, com/tradej/broker/dhan/orders/DhanRestOrderClientFixtureTest, com/tradej/broker/dhan/orders/DhanRestOrderClientUnitTest

### `:broker-upstox` — `broker/upstox/` (58 main · 16 test)
- **`com/tradej/broker/upstox`** (1): UpstoxBrokerConnection
- **`com/tradej/broker/upstox/adapter`** (19): UpstoxFuturesProvider, UpstoxMarginProvider, UpstoxMarketDataProvider, UpstoxOptionsProvider, UpstoxOrderCommandAdapter, UpstoxOrderQueryAdapter, UpstoxPortfolioProvider, UpstoxPriceParser, UpstoxUnsupportedAlerts, UpstoxUnsupportedBracketOrders, UpstoxUnsupportedGttOrders, UpstoxUnsupportedMarginProvider, UpstoxUnsupportedOrderCommand, UpstoxUnsupportedOrderQuery, UpstoxUnsupportedPort, UpstoxUnsupportedPortfolioProvider, UpstoxUnsupportedPorts, UpstoxUnsupportedSessionRisk, UpstoxUnsupportedSliceOrders
- **`com/tradej/broker/upstox/auth`** (10): UpstoxAnalyticsTokenHolder, UpstoxAuthException, UpstoxBearerTokenSource, UpstoxJwtExpiry, UpstoxOAuthClient, UpstoxPkceUtil, UpstoxRedirectServer, UpstoxStaticTokenHolder, UpstoxTokenExpiry, UpstoxTokenManager
- **`com/tradej/broker/upstox/config`** (2): UpstoxApiEnvironment, UpstoxConnectionSettings
- **`com/tradej/broker/upstox/constants`** (1): UpstoxEndpoints
- **`com/tradej/broker/upstox/expired`** (2): UpstoxExpiredOptionMapper, UpstoxExpiredOptionService
- **`com/tradej/broker/upstox/historical`** (2): UpstoxHistoricalCandleMapper, UpstoxHistoricalDataService
- **`com/tradej/broker/upstox/http`** (4): UpstoxApiException, UpstoxHttpClient, UpstoxJsonHttpClient, UpstoxResponseGuard
- **`com/tradej/broker/upstox/instrument`** (4): UpstoxInstrumentDefinition, UpstoxInstrumentLoader, UpstoxInstrumentResolver, UpstoxSegmentMapper
- **`com/tradej/broker/upstox/mapper`** (1): UpstoxDomainMapper
- **`com/tradej/broker/upstox/resilience`** (1): UpstoxResilienceExecutor
- **`com/tradej/broker/upstox/rest`** (6): UpstoxExpiredInstrumentRestClient, UpstoxHistoricalDataRestClient, UpstoxMarketDataRestClient, UpstoxOptionChainRestClient, UpstoxOrderRestClient, UpstoxPortfolioRestClient
- **`com/tradej/broker/upstox/websocket`** (5): ParsedFeedFrame, UpstoxBinaryParser, UpstoxFeedAuthorizer, UpstoxStreamNormalizer, UpstoxWebSocketMultiplexer
- **tests:** com/tradej/broker/upstox/adapter/UpstoxAnalyticsPortsIntegrationTest, com/tradej/broker/upstox/auth/UpstoxAnalyticsTokenHolderTest, com/tradej/broker/upstox/auth/UpstoxJwtExpiryTest, com/tradej/broker/upstox/auth/UpstoxOAuthClientTest, com/tradej/broker/upstox/auth/UpstoxPkceUtilTest, com/tradej/broker/upstox/auth/UpstoxRedirectServerTest, com/tradej/broker/upstox/auth/UpstoxTokenExpiryTest, com/tradej/broker/upstox/expired/UpstoxExpiredOptionMapperTest, com/tradej/broker/upstox/historical/UpstoxHistoricalDataServiceTest, com/tradej/broker/upstox/http/UpstoxHttpClientLiveIntegrationTest, com/tradej/broker/upstox/http/UpstoxResponseGuardTest, com/tradej/broker/upstox/instrument/UpstoxInstrumentKeyResolutionTest, com/tradej/broker/upstox/instrument/UpstoxInstrumentResolverTest, com/tradej/broker/upstox/instrument/UpstoxSegmentMapperTest, com/tradej/broker/upstox/mapper/UpstoxDomainMapperTest, com/tradej/broker/upstox/websocket/UpstoxBinaryParserTest

### `:runtime-disruptor` — `runtime/disruptor/` (15 main · 3 test)
- **`com/tradej/disruptor`** (4): DisruptorBusMetrics, DisruptorEventBus, MutableDomainEventEnvelope, ShardedDisruptorEventBus
- **`com/tradej/disruptor/config`** (11): AsyncDispatchHandler, CandleAggregationDisruptorHandler, ExecutionDisruptorHandler, FeatureSyncDisruptorHandler, GraphPipelineDisruptorHandler, GraphStrategyDisruptorHandler, PositionRiskDisruptorHandler, StageTiming, StageTimings, StrategyDisruptorHandler, SubscriberDispatchHandler
- **tests:** com/tradej/disruptor/DisruptorEventBusStressTest, com/tradej/disruptor/DisruptorGraphReplayParityTest, com/tradej/disruptor/ShardedDisruptorEventBusTest

### `:runtime-hotpath` — `runtime/hotpath/` (5 main · 5 test)
- **`com/tradej/hotpath`** (4): DepthUpdateFactory, MarketDataPipeline, OrderPipeline, PipelineConfig
- **`com/tradej/hotpath/rate`** (1): TokenBucket
- **tests:** com/tradej/hotpath/MarketDataPipelineTest, com/tradej/hotpath/OrderPipelineTest, com/tradej/hotpath/PipelineConfigTest, com/tradej/hotpath/SymbolShardRouterTest, com/tradej/hotpath/rate/TokenBucketTest

### `:trading-strategy` — `trading/strategy/` (18 main · 11 test)
- **`com/tradej/strategy/api`** (3): GraphStrategyPlugin, StrategyPlugin, StrategyPluginAdapter
- **`com/tradej/strategy/example`** (2): DepthImbalanceStrategy, TickPriceChangeStrategy
- **`com/tradej/strategy/ml`** (4): DefaultModelRegistry, InMemoryModelRegistry, MLStrategyPlugin, ThresholdMLInferenceEngine
- **`com/tradej/strategy/node`** (3): CandleNode, PortfolioNode, StrategyNode
- **`com/tradej/strategy/portfolio`** (1): PortfolioEngine
- **`com/tradej/strategy/position`** (1): DefaultPositionSizer
- **`com/tradej/strategy/service`** (4): CandleAggregationService, GraphStrategySandbox, StrategyEngine, StrategySandbox
- **tests:** com/tradej/pipeline/runtime/GraphPipelineReplayParityTest, com/tradej/strategy/ml/MLStrategyPluginTest, com/tradej/strategy/ml/ThresholdMLInferenceEngineTest, com/tradej/strategy/node/CandleNodePassThroughTest, com/tradej/strategy/portfolio/PortfolioEngineStressTest, com/tradej/strategy/portfolio/PortfolioEngineTest, com/tradej/strategy/service/CandleAggregationResamplerParityTest, com/tradej/strategy/service/CandleAggregationServiceComponentTest, com/tradej/strategy/service/CandleAggregationServiceStressTest, com/tradej/strategy/service/GraphStrategySandboxTest, com/tradej/strategy/service/StrategySandboxTest

### `:trading-execution` — `trading/execution/` (17 main · 12 test)
- **`com/tradej/execution/identity`** (2): OrderIdentityRegistry, OrderIdentityRehydrator
- **`com/tradej/execution/node`** (5): FillReconciliationNode, OmsNode, OrderPlacementNode, RiskNode, SignalGateNode
- **`com/tradej/execution/position`** (1): EventSourcedNetPositionProvider
- **`com/tradej/execution/reconcile`** (3): OrderReconciler, ReconciliationAlertLogger, ReconciliationScheduler
- **`com/tradej/execution/risk`** (2): DailyRiskResetScheduler, PositionRiskHandler
- **`com/tradej/execution/service`** (4): CaffeineIdempotencyCache, ExecutionHandler, OrderManagementService, TradingCircuitBreaker
- **tests:** com/tradej/execution/identity/OrderIdentityRegistryStressTest, com/tradej/execution/identity/OrderIdentityRegistryTest, com/tradej/execution/identity/OrderIdentityRehydratorTest, com/tradej/execution/reconcile/OrderReconcilerUnitTest, com/tradej/execution/reconcile/ReconciliationAlertLoggerUnitTest, com/tradej/execution/reconcile/ReconciliationSchedulerComponentTest, com/tradej/execution/reconcile/ReconciliationSchedulerUnitTest, com/tradej/execution/risk/PositionRiskHandlerStressTest, com/tradej/execution/service/ExecutionHandlerStressTest, com/tradej/execution/service/ExecutionHandlerUnitTest, com/tradej/execution/service/TradingCircuitBreakerStressTest, com/tradej/execution/service/TradingCircuitBreakerUnitTest

### `:trading-scanner` — `trading/scanner/` (41 main · 4 test)
- **`com/tradej/scanner/criterion`** (11): CriterionGroup, MaxOiStrikeCriterion, OptionAwareCriterion, PcrRangeCriterion, PctChangeFromOpenCriterion, PctChangeFromPrevCloseCriterion, ScanCriterion, ScanCriterionFactory, ScanCriterionRegistry, StreamingScanCriterion, VolumeSpikeCriterion
- **`com/tradej/scanner/engine`** (3): ScanDependencies, ScanEngine, ScanResultRanker
- **`com/tradej/scanner/fetch`** (2): OptionChainFetcher, SnapshotFetcher
- **`com/tradej/scanner/model`** (13): AssetClass, OptionScanSpec, PromotionSpec, RestScanSpec, ScanAsset, ScanContext, ScanHit, ScanMode, ScanProfile, ScanResult, ScanRun, ScanRunStatus, UniverseSpec
- **`com/tradej/scanner/node`** (3): ScanAggregatorNode, ScanNode, StreamingScanCriterionNode
- **`com/tradej/scanner/option`** (7): LiquidityScorer, OptionContractHit, OptionExpiryPolicy, OptionLiquidityScanner, OptionScanRequest, OptionScanResult, OptionSideFilter
- **`com/tradej/scanner/universe`** (2): IndexConstituentsLoader, UniverseBuilder
- **tests:** com/tradej/scanner/criterion/PcrRangeCriterionTest, com/tradej/scanner/criterion/PctChangeFromOpenCriterionTest, com/tradej/scanner/option/LiquidityScorerTest, com/tradej/scanner/option/OptionLiquidityScannerTest

### `:trading-institutional-scanner` — `trading/institutional-scanner/` (8 main · 5 test)
- **`com/tradej/institutional`** (1): InstitutionalScanEngine
- **`com/tradej/institutional/features`** (1): FeaturePipeline
- **`com/tradej/institutional/model`** (3): InstitutionalScanConfig, InstitutionalScanResult, ScoredBar
- **`com/tradej/institutional/ranking`** (1): RankingEngine
- **`com/tradej/institutional/sector`** (1): SectorRankingEngine
- **`com/tradej/institutional/selection`** (1): CandidateSelection
- **tests:** com/tradej/institutional/InstitutionalScanEngineIntegrationTest, com/tradej/institutional/features/FeaturePipelineTest, com/tradej/institutional/ranking/RankingEngineTest, com/tradej/institutional/sector/SectorRankingEngineTest, com/tradej/institutional/selection/CandidateSelectionTest

### `:trading-indicators` — `trading/indicators/` (6 main · 1 test)
- **`com/tradej/indicators`** (6): BollingerSqueeze, CVD, HalfTrend, HighProbabilityOrderBlock, IndicatorEngine, SwingHighLow
- **tests:** com/tradej/indicators/IndicatorEngineTest

### `:trading-simulation` — `trading/simulation/` (3 main · 1 test)
- **`com/tradej/simulation`** (3): MatchingEngine, PnLLedger, SimulatedOrderService
- **tests:** com/tradej/simulation/MatchingEngineTest

### `:data-persistence` — `data/persistence/` (10 main · 6 test)
- **`com/tradej/persistence/chronicle`** (2): ChronicleAuditLogWriter, ChronicleDeadLetterQueue
- **`com/tradej/persistence/duckdb`** (2): DuckDbEventStore, DuckDbScanStore
- **`com/tradej/persistence/oms`** (1): EventSourcedOrderRepository
- **`com/tradej/persistence/pipeline`** (1): DuckDbPipelineGraphStore
- **`com/tradej/persistence/replay`** (4): HistoricalRangeService, ReplayClock, ReplayResult, ReplayRunner
- **tests:** com/tradej/persistence/duckdb/DuckDbEventStoreTest, com/tradej/persistence/oms/EventSourcedOrderRepositoryStressTest, com/tradej/persistence/oms/EventSourcedOrderRepositoryTest, com/tradej/persistence/pipeline/DuckDbPipelineGraphStoreTest, com/tradej/persistence/replay/ReplayRunnerTest, com/tradej/persistence/replay/ReplayRunnerVirtualClockTest

### `:data-feature-store` — `data/feature-store/` (3 main · 1 test)
- **`com/tradej/feature/store`** (2): DuckDbFeatureStore, InMemoryFeatureStore
- **`com/tradej/feature/store/node`** (1): FeatureNode
- **tests:** com/tradej/feature/store/DuckDbFeatureStoreTest

### `:data-historical-ingest` — `data/historical-ingest/` (31 main · 12 test)
- **`com/tradej/historical/ingest/importing`** (3): HiveCacheEquityImporter, HiveCacheImportConfig, HiveCacheImportResult
- **`com/tradej/historical/ingest/maintenance`** (2): EquityParquetCompactor, UniverseDiskRefresher
- **`com/tradej/historical/ingest/model`** (8): DownloadJobRecord, DownloadJobStats, DownloadJobStatus, DownloadSourceType, DownloadTaskRecord, DownloadTaskStatus, EquityHistoricalDownloadConfig, RollingOptionDownloadConfig
- **`com/tradej/historical/ingest/planner`** (2): EquityHistoricalDownloadPlanner, RollingOptionDownloadPlanner
- **`com/tradej/historical/ingest/query`** (3): EquityHistoricalQuery, HistoricalWarehouseQuery, ParquetHistoricalBarRepository
- **`com/tradej/historical/ingest/resample`** (1): CandleResampler
- **`com/tradej/historical/ingest/service`** (3): DownloadJobRegistry, DownloadJobService, EquityDownloadJobService
- **`com/tradej/historical/ingest/store`** (2): DuckDbHistoricalWarehouse, ParquetBarWriter
- **`com/tradej/historical/ingest/universe`** (7): HistoricalEquityPaths, HivePartitionResolver, LocalUniverseImporter, Nifty500Constituent, Nifty500UniverseFetcher, UniverseRefreshService, UniverseSnapshotWriter
- **tests:** com/tradej/historical/ingest/importing/HiveCacheEquityImporterTest, com/tradej/historical/ingest/importing/HiveCacheLocalIntegrationTest, com/tradej/historical/ingest/maintenance/EquityParquetCompactorTest, com/tradej/historical/ingest/planner/EquityHistoricalDownloadPlannerTest, com/tradej/historical/ingest/planner/RollingOptionDownloadPlannerTest, com/tradej/historical/ingest/query/ParquetHistoricalBarRepositoryComponentTest, com/tradej/historical/ingest/resample/CandleResamplerTest, com/tradej/historical/ingest/store/DuckDbHistoricalWarehouseLegacyMigrationTest, com/tradej/historical/ingest/store/DuckDbHistoricalWarehouseTaskClaimTest, com/tradej/historical/ingest/store/DuckDbHistoricalWarehouseTest, com/tradej/historical/ingest/universe/HivePartitionResolverTest, com/tradej/historical/ingest/universe/Nifty500UniverseFetcherTest

### `:data-analytics` — `data/analytics/` (8 main · 1 test)
- **`com/tradej/analytics/catalog`** (1): HistoricalDataCatalog
- **`com/tradej/analytics/config`** (1): DuckDbAnalyticsConfig
- **`com/tradej/analytics/engine`** (1): DuckDbAnalyticsEngine
- **`com/tradej/analytics/guard`** (1): AnalyticsSqlGuard
- **`com/tradej/analytics/pipeline`** (1): AnalyticsPipelineSharedState
- **`com/tradej/analytics/repository`** (2): DuckDbRollingOptionHistoricalRepository, FederatedHistoricalBarRepository
- **`com/tradej/analytics/service`** (1): DefaultHistoricalAnalyticsService
- **tests:** com/tradej/analytics/guard/AnalyticsSqlGuardTest

### `:app` — `app/` (82 main · 65 test)
- **`com/tradej/app`** (1): TradingApplication
- **`com/tradej/app/admin`** (4): AdminController, DashboardRedirectController, HistoricalDownloadController, RuntimeHealthState
- **`com/tradej/app/api`** (9): AnalyticsController, ExpiredOptionsController, MarketDataController, OptionScanController, PipelineController, ReadModelController, ScanController, StudioController, SymbolController
- **`com/tradej/app/config`** (29): AnalyticsConfiguration, BrokerConfiguration, BrokerMarketDataConfiguration, BrokerRuntimeMode, BrokerRuntimeModeResolver, ClockConfiguration, EventBusConfiguration, ExecutionConfiguration, FeatureStoreConfiguration, HistoricalDownloadConfiguration, OptionScanConfiguration, PersistenceConfiguration, PortfolioConfiguration, PropertiesBrokerCapabilities, RateLimitFilter, ReadModelConfiguration, RiskConfiguration, RuntimeConfiguration, RuntimeModeConfiguration, ScanConfiguration, ScanProperties, SchedulingConfiguration, StartupConfiguration, StrategyConfiguration, StudioConfiguration, TradingProperties, TradingRuntimeConfiguration, UpstoxConfiguration, WorkspaceConfiguration
- **`com/tradej/app/health`** (6): AnalyticsHealthIndicator, BrokerErrorTracker, BrokerHealthIndicator, MarketDataHealthIndicator, OrderPipelineHealthIndicator, UpstoxHealthIndicator
- **`com/tradej/app/metrics`** (4): MicrometerConfiguration, ObservableMarketDataProvider, ObservableOrderCommand, StageTimingConfiguration
- **`com/tradej/app/pipeline`** (9): DagPipelineIngressBridge, DagPipelineInstance, DagPipelineRuntimeService, PipelineCompileContexts, PipelineConfiguration, PipelineNodeFactory, PipelineRuntimeService, PositionStateRebuilder, ReplayOrchestrator
- **`com/tradej/app/pipeline/reactor`** (3): ReactorBridgeMetrics, ReactorColdPathConsumer, ReactorColdPathRegistry
- **`com/tradej/app/readmodel`** (1): ReadModelStore
- **`com/tradej/app/scanner`** (7): OptionScanService, RuntimeSubscriptionManager, ScanProfileCatalog, ScanProfileMapper, ScanResultStore, ScanScheduler, ScanService
- **`com/tradej/app/service`** (2): BrokerCapabilityLimits, OrderEventJournal
- **`com/tradej/app/service/broker`** (5): BrokerExpiredOptionQueryService, BrokerHistoricalQueryService, LivePnlService, MarketDepthOrchestrator, OptionStrikeResolver
- **`com/tradej/app/startup`** (1): BrokerStartupOrchestrator
- **`com/tradej/app/studio`** (1): StudioChartService
- **tests:** 65 files under `src/test/java`

### `:gateway` — `gateway/` (7 main · 0 test)
- **`com/tradej/gateway/bridge`** (1): GatewayEventBridge
- **`com/tradej/gateway/config`** (2): GatewayProperties, GatewayWebSocketConfiguration
- **`com/tradej/gateway/protocol`** (2): GatewayBinaryCodec, GatewayTopic
- **`com/tradej/gateway/router`** (1): GatewayTopicRouter
- **`com/tradej/gateway/websocket`** (1): GatewayWebSocketHandler

### `:cli` — `cli/` (19 main · 6 test)
- **`com/tradej/cli`** (3): CliContext, CliOperations, TradeCli
- **`com/tradej/cli/analytics`** (1): CliAnalyticsSupport
- **`com/tradej/cli/attach`** (1): AttachClient
- **`com/tradej/cli/config`** (1): CliConfig
- **`com/tradej/cli/download`** (2): CliDownloadSupport, CliEquityImportSupport
- **`com/tradej/cli/interactive`** (2): InteractiveShell, MenuContext
- **`com/tradej/cli/output`** (2): OutputFormatter, TablePrinter
- **`com/tradej/cli/scan`** (1): ScanProfileJsonLoader
- **`com/tradej/cli/standalone`** (6): BrokerSession, BrokerSessionFactory, DhanBrokerSession, NoOpIdempotencyCache, UpstoxBrokerSession, UpstoxCliConnectionFactory
- **tests:** com/tradej/cli/CandlesCmdRangeTest, com/tradej/cli/attach/AttachClientTest, com/tradej/cli/config/CliConfigBrokerTest, com/tradej/cli/output/TablePrinterTest, com/tradej/cli/scan/OptionScanProfileLoaderTest, com/tradej/cli/standalone/BrokerSessionFactoryTest

### `:trade-pipeline-platform` — `pipeline/platform/trade-pipeline-platform/` (15 main · 12 test)
- **`com/tradej/pipeline/platform`** (7): PipelineDefinition, PipelineSnapshot, PipelineStatus, PipelineTemplate, PipelineTemplateService, PipelineType, PipelineVersion
- **`com/tradej/pipeline/platform/catalog`** (3): InMemoryPipelineStore, PipelineCatalogService, PipelineStore
- **`com/tradej/pipeline/platform/model`** (5): PipelineArtifact, PipelineExecution, PipelineExecutionStatus, PropertyDescriptor, ValidationRule
- **tests:** com/tradej/pipeline/platform/ModuleDependencyTest, com/tradej/pipeline/platform/PipelineSnapshotUnitTest, com/tradej/pipeline/platform/PipelineTemplateServiceTest, com/tradej/pipeline/platform/PipelineTemplateTest, com/tradej/pipeline/platform/PipelineVersionTest, com/tradej/pipeline/platform/PipelineVersionUnitTest, com/tradej/pipeline/platform/catalog/InMemoryPipelineStoreTest, com/tradej/pipeline/platform/catalog/PipelineCatalogServiceTest, com/tradej/pipeline/platform/model/PipelineExecutionStatusUnitTest, com/tradej/pipeline/platform/model/PipelineExecutionUnitTest, com/tradej/pipeline/platform/model/PropertyDescriptorUnitTest, com/tradej/pipeline/platform/model/ValidationRuleUnitTest

### `:trade-analytics` — `pipeline/analytics/trade-analytics/` (9 main · 6 test)
- **`com/tradej/analytics`** (9): DefaultDrawdownAnalytics, DefaultPerformanceAnalytics, DrawdownAnalytics, DrawdownReport, EquityPoint, PerformanceAnalytics, PerformanceReport, TradeRecord, package-info
- **tests:** com/tradej/analytics/DefaultDrawdownAnalyticsTest, com/tradej/analytics/DefaultPerformanceAnalyticsTest, com/tradej/analytics/DrawdownReportTest, com/tradej/analytics/PerformanceReportTest, com/tradej/analytics/PerformanceReportUnitTest, com/tradej/analytics/TradeRecordTest

### `:trade-node-library` — `nodes/trade-node-library/` (12 main · 4 test)
- **`com/tradej/node`** (7): NodeCategory, NodeContext, NodeDescriptor, NodeExecutor, NodeResult, PortDescriptor, package-info
- **`com/tradej/node/adapter`** (1): NodeAdapterFactory
- **`com/tradej/node/data`** (1): HistoricalDataNode
- **`com/tradej/node/feature`** (1): FeatureNode
- **`com/tradej/node/output`** (1): OutputNode
- **`com/tradej/node/scanner`** (1): ScannerNode
- **tests:** com/tradej/node/NodeDescriptorTest, com/tradej/node/NodeResultTest, com/tradej/node/output/OutputNodeTest, com/tradej/node/scanner/ScannerNodeTest

### `:trade-experiments` — `research/experiment/trade-experiments/` (5 main · 3 test)
- **`com/tradej/experiments`** (5): Experiment, ExperimentRun, ExperimentRunStatus, ExperimentService, ExperimentStatus
- **tests:** com/tradej/experiments/ExperimentRunTest, com/tradej/experiments/ExperimentServiceTest, com/tradej/experiments/ExperimentTest

### `:trade-optimization` — `research/optimizer/trade-optimization/` (12 main · 5 test)
- **`com/tradej/optimizer`** (12): GridSearchOptimizationEngine, MonteCarloOptimizationEngine, OptimizationEngine, OptimizationJob, OptimizationResult, OptimizationStatus, OptimizationStrategy, ParameterSpace, ParameterType, TrialResult, WalkForwardOptimizationEngine, package-info
- **tests:** com/tradej/optimizer/GridSearchOptimizationEngineTest, com/tradej/optimizer/MonteCarloOptimizationEngineTest, com/tradej/optimizer/OptimizationJobTest, com/tradej/optimizer/OptimizationResultTest, com/tradej/optimizer/TrialResultTest

### `:architecture-test` — `architecture-test/` (0 main · 1 test)
- `com/tradej/architecture/ModuleBoundaryArchitectureTest.java`

### `frontend/` — React console (30 files, synced into `:app` via `syncFrontend`)
- `src/api/client.ts`
- `src/api/sse.ts`
- `src/api/websocket.ts`
- `src/dto/types.test.ts`
- `src/dto/types.ts`
- `src/hooks/useSSE.ts`
- `src/store/usePipelineStore.ts`
- `src/store/useStudioStore.test.ts`
- `src/store/useStudioStore.ts`
- `src/test-setup.ts`
- `vite.config.ts`
- `src/App.tsx`
- `src/charts/ChartWidget.tsx`
- `src/components/AdminPanel.test.tsx`
- `src/components/AdminPanel.tsx`
- `src/components/CommandPalette.test.tsx`
- `src/components/CommandPalette.tsx`
- `src/components/ErrorBoundary.test.tsx`
- `src/components/ErrorBoundary.tsx`
- `src/components/LeftSidebar.test.tsx`
- `src/components/LeftSidebar.tsx`
- `src/components/PipelinePanel.test.tsx`
- `src/components/PipelinePanel.tsx`
- `src/components/ScannerPanel.test.tsx`
- `src/components/ScannerPanel.tsx`
- `src/components/TradingPanel.test.tsx`
- `src/components/TradingPanel.tsx`
- `src/main.tsx`
- `dist/assets/index-DEvstAM6.css`
- `src/index.css`
