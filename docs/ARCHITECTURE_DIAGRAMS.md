# Trade-J Architecture Diagrams

*Mermaid diagrams for visual architecture documentation — June 2026*

---

## 1. High-Level System Architecture (Component Diagram)

```mermaid
graph TB
    subgraph Clients["Client Layer"]
        CLI["CLI<br/>(TradeCli)"]
        REST["REST API<br/>(Spring Boot Controllers)"]
        WS_FE["Frontend<br/>(WebSocket Client)"]
    end

    subgraph Composition["Composition Layer"]
        FC["FullComposition"]
        BC["BrokerComposition"]
        DC["DataComposition"]
        EC["ExecutionComposition"]
        PC["PipelineComposition"]
        CC["ClockComposition"]
    end

    subgraph GatewayLayer["Gateway Layer"]
        BG["BrokerGateway"]
        BH["BrokerHandle"]
        BCS["BrokerCallSupport"]
        GEB["GatewayEventBridge"]
        GTR["GatewayTopicRouter"]
    end

    subgraph SpringBoot["Spring Boot Application (41 Config Classes)"]
        SC["StartupConfiguration"]
        HC["BrokerHealthIndicator"]
        AM["AlertManager"]
    end

    subgraph EventInfra["Event Infrastructure"]
        DEB["DisruptorEventBus<br/>(8192 ring buffer)"]
        SEB["SimpleEventBus<br/>(testing)"]
        HP_MDP["MarketDataPipeline"]
        HP_ORD["OrderPipeline"]
    end

    subgraph Pipeline["Pipeline Runtime"]
        DAG["DagPipelineRuntimeService"]
        NR["NodeRegistry"]
        RB["ReactorBridge"]
        VC["VirtualClock"]
    end

    subgraph Strategy["Strategy Layer"]
        GSS["GraphStrategySandbox"]
        GSP["GraphStrategyPlugin"]
        ML["MLStrategyPlugin"]
        PS["PositionSizer"]
    end

    subgraph Execution["Execution Layer"]
        EH["ExecutionHandler"]
        OMS["OrderManagementService"]
        KSC["KillSwitchCoordinator"]
        RCC["RiskCheckChain"]
        PRH["PositionRiskHandler"]
        OR["OrderReconciler"]
    end

    subgraph Scanner["Scanner Layer"]
        SE["ScanEngine"]
        SC2["ScanCriterion"]
        ISP["InstitutionalScanner"]
    end

    subgraph DataLayer["Data Layer"]
        PDS["ParquetHistoricalDataStore"]
        DDE["DuckDbAnalyticsEngine"]
        DES["DuckDbEventStore"]
        CAW["ChronicleAuditLogWriter"]
        DLQ["ChronicleDeadLetterQueue"]
        FST["FeatureStore"]
    end

    subgraph BrokerAbstraction["Broker Abstraction"]
        IBC["IBrokerConnection"]
        CM["CapabilityMap"]
        BP["BrokerProvider SPI"]
    end

    subgraph BrokerImpl["Broker Implementations"]
        DHAN["DhanBrokerConnection<br/>(60+ files)"]
        UPSTOX["UpstoxBrokerConnection<br/>(45+ files)"]
        ICICI["IciciBrokerConnection<br/>(40+ files)"]
    end

    CLI --> Composition
    REST --> SpringBoot
    WS_FE --> GEB

    Composition --> BG
    SpringBoot --> BG

    BG --> BH --> BCS --> IBC
    GEB --> GTR

    IBC --> CM
    CM --> DHAN
    CM --> UPSTOX
    CM --> ICICI

    DHAN --> DEB
    UPSTOX --> DEB
    ICICI --> DEB

    DEB --> GSS
    DEB --> EH
    DEB --> GEB

    EH --> OMS --> IBC
    EH --> RCC --> KSC --> PRH

    GSS --> GSP
    GSS --> ML
    GSS --> PS

    DC --> PDS
    DC --> DDE
    DC --> DES
    DC --> CAW

    DAG --> NR --> RB
    RB --> VC
```

---

## 2. Module Dependency Graph

```mermaid
graph BT
    core["core<br/>(leaf — no deps)"]

    broker_api["broker-api"]
    broker_core["broker-core"]
    broker_dhan["broker-dhan"]
    broker_upstox["broker-upstox"]
    broker_icici["broker-icici"]
    broker_gw["broker-gateway"]

    runtime_disruptor["runtime-disruptor"]
    runtime_hotpath["runtime-hotpath"]

    pipeline_core["pipeline-core"]
    pipeline_rt["pipeline-runtime"]
    pipeline_plat["trade-pipeline-platform"]
    pipeline_analytics["trade-analytics"]

    trading_strategy["trading-strategy"]
    trading_exec["trading-execution"]
    trading_ind["trading-indicators"]
    trading_scan["trading-scanner"]
    trading_inst["trading-institutional-scanner"]
    trading_opts["trading-options-analytics"]
    trading_sim["trading-simulation"]

    data_persist["data-persistence"]
    data_analytics["data-analytics"]
    data_ingest["data-historical-ingest"]
    data_feat["data-feature-store"]

    node_lib["trade-node-library"]
    replay["replay-engine"]
    composition["composition"]
    gateway["gateway"]
    cli["cli"]
    app["app"]

    broker_api --> core
    broker_core --> broker_api
    broker_core --> core
    broker_dhan --> broker_core
    broker_upstox --> broker_core
    broker_icici --> broker_core
    broker_gw --> broker_api

    runtime_disruptor --> core
    runtime_hotpath --> runtime_disruptor
    runtime_hotpath --> core

    pipeline_core --> core
    pipeline_rt --> pipeline_core
    pipeline_rt --> core
    pipeline_plat --> pipeline_core
    pipeline_analytics --> pipeline_core

    trading_strategy --> core
    trading_exec --> broker_api
    trading_exec --> core
    trading_ind --> core
    trading_scan --> core
    trading_scan --> broker_api
    trading_inst --> core
    trading_inst --> broker_api
    trading_opts --> core
    trading_sim --> core

    data_persist --> core
    data_analytics --> data_persist
    data_analytics --> core
    data_ingest --> data_persist
    data_ingest --> broker_api
    data_feat --> data_persist

    node_lib --> pipeline_core
    replay --> core
    replay --> pipeline_rt

    composition --> broker_dhan
    composition --> broker_upstox
    composition --> broker_icici
    composition --> trading_exec
    composition --> data_persist
    composition --> pipeline_rt

    gateway --> core
    gateway --> runtime_disruptor
    gateway --> broker_gw

    cli --> composition
    cli --> broker_gw

    app --> composition
    app --> broker_dhan
    app --> broker_upstox
    app --> broker_icici
    app --> gateway
    app --> cli
    app --> runtime_disruptor
    app --> pipeline_rt
    app --> trading_strategy
    app --> trading_exec
    app --> trading_scan
    app --> data_persist
    app --> data_analytics
    app --> data_ingest

    style core fill:#e1f5fe
    style broker_api fill:#fff3e0
    style broker_core fill:#fff3e0
    style broker_dhan fill:#fce4ec
    style broker_upstox fill:#fce4ec
    style broker_icici fill:#fce4ec
    style broker_gw fill:#f3e5f5
    style gateway fill:#f3e5f5
    style composition fill:#e8f5e9
    style app fill:#fff9c4
```

---

## 3. Market Data Flow (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    participant Broker as Broker WebSocket
    participant MDP as MarketDataPipeline<br/>(HotPath)
    participant TB as TokenBucket<br/>(Rate Limiter)
    participant DEB as DisruptorEventBus<br/>(8192 ring buffer)
    participant GSS as GraphStrategySandbox
    participant DAG as DagPipelineRuntime
    participant DAP as DepthAnalyticsPipeline
    participant CAS as CandleAggregationService
    participant GEB as GatewayEventBridge
    participant FE as Frontend

    Broker->>MDP: MarketTickEvent (LTP, volume)
    MDP->>TB: Check rate limit
    alt Rate limit exceeded
        TB-->>MDP: Shed tick
    else Within limit
        MDP->>DEB: publish(MarketTickEvent)
        DEB->>DEB: Ring Buffer enqueue
        DEB->>GSS: Dispatch to strategy plugins
        GSS->>GSS: Filter by subscribedEventTypes()
        GSS->>GSS: Execute in virtual thread (5s timeout)
        GSS-->>DEB: SignalGenerated event
        DEB->>DAG: Dispatch to pipeline nodes
        DEB->>DAP: DepthUpdateEvent
        DAP->>DAP: Imbalance, Absorption, Iceberg detection
        DAP-->>DEB: DepthAnalyticsEvents
    end

    Broker->>MDP: DepthUpdateEvent (bids/asks)
    MDP->>DEB: publish(DepthUpdateEvent)
    DEB->>GEB: Dispatch
    GEB->>GEB: GatewayBinaryCodec.encode()
    GEB->>FE: Binary WebSocket frame

    Note over CAS: After N ticks or time interval
    CAS->>DEB: publish(CandleClosed)
    DEB->>GEB: Dispatch
    GEB->>FE: Candle update
```

---

## 4. Order Execution Flow (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    participant STRAT as GraphStrategySandbox
    participant EH as ExecutionHandler
    participant RCC as RiskCheckChain
    participant KS as KillSwitchCoordinator
    participant DL as DailyLossRiskCheck
    participant PL as PositionLimitRiskCheck
    participant MEH as MarginEnforcementHandler
    participant OMS as OrderManagementService
    participant OSM as OrderStateMachine
    participant IBC as IBrokerConnection<br/>(OrderCommand)
    participant JRN as OrderEventJournal
    participant DEB as DisruptorEventBus

    STRAT->>EH: SignalGenerated event
    EH->>EH: Queue command (partitioned BlockingQueue)
    EH->>EH: Worker thread picks up command

    EH->>RCC: findRejection(RiskContext)
    RCC->>KS: RiskCheck #1 — Kill switch?
    alt Kill switch engaged
        KS-->>RCC: REJECT (KillSwitchEngaged)
        RCC-->>EH: Optional.of(rejection)
        EH->>DEB: publish(SignalSuppressed)
    else Kill switch clear
        KS-->>RCC: PASS
        RCC->>DL: RiskCheck #2 — Daily loss limit?
        alt Limit exceeded
            DL-->>RCC: REJECT
            RCC-->>EH: Optional.of(rejection)
            EH->>DEB: publish(SignalSuppressed)
        else Within limits
            DL-->>RCC: PASS
            RCC->>PL: RiskCheck #3 — Position limits?
            alt Too many positions
                PL-->>RCC: REJECT
            else Room available
                PL-->>RCC: PASS
                RCC-->>EH: Optional.empty()
            end
        end
    end

    Note over EH: All risk checks passed

    EH->>MEH: Check margin availability
    MEH->>IBC: margin().estimateMargin(request)
    IBC-->>MEH: MarginEstimate
    alt Insufficient margin
        MEH-->>EH: Margin insufficient
        EH->>DEB: publish(SignalSuppressed)
    else Sufficient margin
        MEH-->>EH: OK

        EH->>OMS: placeOrder(request)
        OMS->>OMS: normalize symbol
        OMS->>OSM: transition(NEW → SUBMITTED)
        OMS->>IBC: orders().placeOrder(...)
        IBC->>IBC: REST API call to broker
        IBC-->>OMS: OrderResponse

        alt Order accepted
            OMS->>OSM: transition(SUBMITTED → ACCEPTED)
            OMS->>JRN: Journal order event
            OMS->>DEB: publish(OrderAccepted)
        else Order rejected
            OMS->>OSM: transition(SUBMITTED → REJECTED)
            OMS->>DEB: publish(OrderRejected)
        end
    end
```

---

## 5. Historical Data Flow (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    participant CLI as CLI / Scheduler
    participant DJS as DownloadJobService
    participant EQP as EquityHistoricalDownloadPlanner
    participant ROP as RollingOptionDownloadPlanner
    participant BQS as BrokerHistoricalQueryService
    participant IBC as IBrokerConnection<br/>(MarketDataProvider)
    participant PWS as ParquetWriteService
    participant PDS as ParquetHistoricalDataStore
    participant DHW as DuckDbHistoricalWarehouse
    participant DAE as DuckDbAnalyticsEngine

    CLI->>DJS: Trigger download job
    DJS->>EQP: Plan equity downloads
    EQP->>EQP: Check last known date per symbol
    EQP->>EQP: Identify gaps via GapDetector
    EQP-->>DJS: DownloadTaskRecord[]

    DJS->>ROP: Plan option downloads
    ROP->>ROP: Check rolling option expiries
    ROP-->>DJS: DownloadTaskRecord[]

    loop For each task
        DJS->>BQS: Download historical candles
        BQS->>IBC: marketData().getCandles(symbol, interval, from, to)
        IBC->>IBC: Broker REST API call
        IBC-->>BQS: Candle[]

        BQS-->>DJS: Raw candle data
        DJS->>PWS: Write candles
        PWS->>PWS: Canonical format conversion
        PWS->>PDS: Write to Parquet
        PDS->>PDS: Hive partitioning<br/>segment=NSE_EQ/symbol=X/data_0.parquet
        PDS-->>DJS: Write complete
    end

    Note over DHW: On query
    CLI->>DAE: Analytics query (SQL or API)
    DAE->>DHW: Execute SQL with read_parquet()
    DHW->>PDS: Read Parquet files via glob
    PDS-->>DHW: Columnar data
    DHW-->>DAE: Query results
    DAE-->>CLI: AnalyticsQueryResult
```

---

## 6. Broker Initialization Sequence

```mermaid
sequenceDiagram
    autonumber
    participant CFG as ConfigLoader
    participant BP as BrokerProfile
    participant FC as FullComposition
    participant BC as BrokerComposition
    participant AUTH as TokenManager<br/>(DhanTokenManager)
    participant CLIENT as DhanAuthClient
    participant IBC as DhanBrokerConnection
    participant WS as WebSocketMultiplexer
    participant MDP as MarketDataProvider
    participant ORDS as OrderCommand
    participant INST as InstrumentResolver

    CFG->>BP: Load BrokerProfile
    BP->>BP: validate() — check required fields

    FC->>BC: create(BrokerProfile, IdempotencyCache)
    BC->>BC: Select broker type = DHAN
    BC->>AUTH: new DhanTokenManager(settings)

    alt AuthMode.STATIC
        AUTH->>AUTH: Use config-provided token
    else AuthMode.TOTP
        AUTH->>CLIENT: generateViaTotp(clientId, pin, totp)
        CLIENT->>CLIENT: POST /generateToken
        CLIENT-->>AUTH: Access token
        AUTH->>AUTH: DhanTokenStateStore.save()
    end

    AUTH->>AUTH: ensureValid()
    AUTH-->>BC: Token ready

    BC->>IBC: new DhanBrokerConnection(adapters...)
    IBC->>IBC: Build CapabilityMap
    Note over IBC: Registers 15+ capabilities:<br/>MarketDataProvider, OrderCommand,<br/>OptionsProvider, FuturesProvider,<br/>BracketOrderProvider, GttOrderProvider, etc.

    BC-->>FC: IBrokerConnection ready
    FC-->>CFG: Composition complete

    Note over IBC: On connect()
    IBC->>WS: WebSocketMultiplexer.connect()
    WS->>WS: Establish WebSocket connection
    WS->>WS: Start health monitor
    WS->>MDP: Subscribe market data feeds
    WS->>ORDS: Subscribe order updates
```

---

## 7. Event Bus Architecture

```mermaid
graph TB
    subgraph Producers["Event Producers"]
        WS_DAHN["Dhan WebSocket"]
        WS_UPSTOX["Upstox WebSocket"]
        WS_ICICI["ICICI WebSocket"]
        STRAT_PLUG["Strategy Plugins"]
        SCANNER["Scan Engine"]
        CANDLE["CandleAggregation"]
        RECON["OrderReconciler"]
    end

    subgraph Disruptor["DisruptorEventBus (Production)"]
        direction TB
        RB["Ring Buffer<br/>Capacity: 8192<br/>ProducerType: MULTI"]
        REG["Re-entrancy Guard<br/>(ThreadLocal&lt;Boolean&gt;)"]
        DQ["Downstream Queue<br/>ArrayBlockingQueue(4096)"]
        GS["GraphStrategy<br/>DisruptorHandler"]
        AD["AsyncDispatch<br/>Handler"]
        SEEN["Dedup Map<br/>(Scheduled pruner)"]
        DLQ2["Dead Letter Queue<br/>(Chronicle)"]

        RB --> REG
        REG --> GS
        GS --> AD
        AD --> SEEN
        REG -.->|re-entrant| DQ
        DQ -.->|overflow| DLQ2
    end

    subgraph SimpleBus["SimpleEventBus (Testing)"]
        CM["ConcurrentHashMap<br/>eventType → subscribers"]
        COW["CopyOnWriteArrayList"]
        CM --> COW
    end

    subgraph Consumers["Event Consumers"]
        direction TB
        subgraph MarketData["MarketDataVisitor"]
            MTE["MarketTickEvent"]
            DUE["DepthUpdateEvent"]
            CC["CandleClosed"]
            CD["CandleDeveloping"]
            OCU["OptionChainUpdated"]
            GC["GreeksComputed"]
            GEC["GammaExposureComputed"]
            MPC["MaxPainComputed"]
        end

        subgraph OrderLifecycle["OrderLifecycleVisitor"]
            OA["OrderAccepted"]
            OF["OrderFilled"]
            ORJ["OrderRejected"]
            OC2["OrderCancelled"]
            OM2["OrderModified"]
            OPF["OrderPartiallyFilled"]
            OFF2["OrderFullyFilled"]
            OUE["OrderUpdateEvent"]
        end

        subgraph Trading["TradingVisitor"]
            SG["SignalGenerated"]
            SPE["SignalPendingExecution"]
            SS["SignalSuppressed"]
            TOO["TradeOpened"]
            TCO["TradeClosed"]
            TUP["TradeUpdated"]
            KSE["KillSwitchEngaged"]
            UKE["UnifiedKillSwitchEngaged"]
            UKD["UnifiedKillSwitchDisengaged"]
            UPD["UnrealizedPnLUpdated"]
            PE["PnlUpdatedEvent"]
            PUE["PositionUpdateEvent"]
            PM["PositionMismatch"]
            TEE["TradeExecutionEvent"]
        end

        subgraph System["SystemVisitor"]
            RHR["ReconciliationHaltRequired"]
            SHC["StreamHealthChanged"]
            EBB["EventBusBackpressure"]
            BAE["BrokerAdapterError"]
            SE2["StrategyError"]
            SHP["ScanHitProduced"]
            SRP["ScanResultsPublished"]
            RTC["ReplayTimeChangedEvent"]
        end
    end

    Producers --> RB
    AD --> MarketData
    AD --> OrderLifecycle
    AD --> Trading
    AD --> System

    style RB fill:#ff6b6b,color:#fff
    style REG fill:#ffd93d
    style AD fill:#6bcb77,color:#fff
    style DLQ2 fill:#ff6b6b,color:#fff
```

---

## 8. Composition Layer Flow

```mermaid
sequenceDiagram
    autonumber
    participant USER as User (CLI or Spring)
    participant FC as FullComposition
    participant CFG as ConfigLoader
    participant BC as BrokerComposition
    participant DC as DataComposition
    participant EC as ExecutionComposition
    participant PC as PipelineComposition
    participant CC as ClockComposition

    USER->>FC: createFull(brokerProfile, storageProfile, riskProfile)
    FC->>CFG: Load configuration
    CFG-->>FC: Validated profiles

    par Create sub-compositions in parallel
        FC->>CC: ClockComposition.create()
        CC->>CC: VirtualClock (LIVE mode)
        CC->>CC: TradingClock
        CC->>CC: EventMetadataFactory

        FC->>BC: BrokerComposition.create(profile, cache)
        BC->>BC: Select broker type
        BC->>BC: Initialize token manager
        BC->>BC: Create IBrokerConnection
        BC->>BC: Create BrokerLifecycleManager

        FC->>DC: DataComposition.create(storageProfile)
        DC->>DC: ChronicleDeadLetterQueue
        DC->>DC: ChronicleAuditLogWriter
        DC->>DC: DuckDbConnectionPool
        DC->>DC: AsyncDuckDbEventStore
        DC->>DC: DuckDbPipelineGraphStore
        DC->>DC: DuckDbScanStore

        FC->>EC: ExecutionComposition.create(broker, riskProfile)
        EC->>EC: EventSourcedNetPositionProvider
        EC->>EC: MarginEnforcementHandler
        EC->>EC: KillSwitchCoordinator
        EC->>EC: PositionRiskHandler (aggregates all risk)
        EC->>EC: CaffeineIdempotencyCache
    end

    FC->>PC: PipelineComposition.create(clock, broker, ...)
    PC->>PC: VirtualClock, ReactorBridge
    PC->>PC: NodeRegistry
    PC->>PC: PipelineNodeFactory
    PC->>PC: DagPipelineRuntimeService

    FC-->>USER: FullComposition (ready)

    Note over USER: CLI usage
    USER->>FC: brokerConnection()
    FC-->>USER: IBrokerConnection
    USER->>FC: lifecycleManager()
    FC-->>USER: BrokerLifecycleManager
```

---

## 9. Strategy Execution Flow

```mermaid
sequenceDiagram
    autonumber
    participant DEB as DisruptorEventBus
    participant GSDH as GraphStrategy<br/>DisruptorHandler
    participant GSS as GraphStrategySandbox
    participant SL as ServiceLoader
    participant STRAT1 as TickPriceChange<br/>Strategy
    participant STRAT2 as DepthImbalance<br/>Strategy
    participant STRAT3 as MLStrategy<br/>(Threshold)
    participant STRAT4 as OptionsContext<br/>Strategy
    participant PS as PositionSizer
    participant DEB2 as DisruptorEventBus
    participant EH as ExecutionHandler

    Note over DEB,GSS: Event arrives via Disruptor ring buffer

    DEB->>GSDH: DomainEvent (any type)
    GSDH->>GSS: onDomainEvent(event)

    GSS->>GSS: Extract event type from event.getClass()

    par Fan out to matching plugins
        GSS->>STRAT1: onDomainEvent(event)
        Note over STRAT1: subscribedEventTypes:<br/>{MarketTickEvent, CandleClosed}
        alt event is MarketTickEvent
            STRAT1->>STRAT1: Evaluate price change signal
            STRAT1->>PS: Size position (PositionSizer)
            PS->>PS: Calculate quantity based on risk limits
            PS-->>STRAT1: Sized signal
            STRAT1->>DEB2: publish(SignalGenerated)
            DEB2->>EH: Signal → Order execution flow
        end

        GSS->>STRAT2: onDomainEvent(event)
        Note over STRAT2: subscribedEventTypes:<br/>{DepthUpdateEvent}
        alt event is DepthUpdateEvent
            STRAT2->>STRAT2: Analyze bid/ask imbalance
            STRAT2->>DEB2: publish(SignalGenerated)
        end

        GSS->>STRAT3: onDomainEvent(event)
        Note over STRAT3: subscribedEventTypes:<br/>{CandleClosed}
        alt event is CandleClosed
            STRAT3->>STRAT3: Run ML inference engine
            STRAT3->>STRAT3: Compare threshold: RSI < 30 AND EMA crossover
            STRAT3->>DEB2: publish(SignalGenerated)
        end

        GSS->>STRAT4: onDomainEvent(event)
        Note over STRAT4: subscribedEventTypes:<br/>{OptionChainUpdated}
        alt event is OptionChainUpdated
            STRAT4->>STRAT4: Evaluate options context
            STRAT4->>DEB2: publish(SignalGenerated)
        end
    end

    Note over GSS: Each plugin runs in its own<br/>virtual thread with 5s timeout
```

---

## 10. Historical Replay Flow

```mermaid
sequenceDiagram
    autonumber
    participant CLI as CLI / REST API
    participant RO as ReplayOrchestrator
    participant VC as VirtualClock
    participant CRS as CandleReplaySession
    participant HCL as HistoricalCandleLoader
    participant PDS as ParquetHistoricalDataStore
    participant DHW as DuckDbHistoricalWarehouse
    participant CSB as ClockSyncedEventBus
    participant DEB as DisruptorEventBus
    participant GSS as GraphStrategySandbox
    participant EH as ExecutionHandler
    participant SIM as SimulatedOrderService
    participant GW as GatewayTopicRouter
    participant FE as Frontend

    CLI->>RO: startCandleReplay(symbol, interval, speed)
    RO->>VC: switchToReplay()
    RO->>VC: Initialize ReplayStateManager
    RO->>CSB: Wrap EventBus with clock sync

    RO->>CRS: new CandleReplaySession(candles, clock)
    CRS->>HCL: loadCandles(symbol, interval, dateRange)
    HCL->>DHW: SQL query with read_parquet()
    DHW->>PDS: Read Parquet files
    PDS-->>DHW: Candle[]
    DHW-->>HCL: Candle[]
    HCL-->>CRS: List<Candle>

    CRS->>GW: Broadcast "replay started"
    GW->>FE: ReplayStatusEvent

    loop Scheduled playback (adjustable speed)
        CRS->>CRS: step() — advance to next candle
        CRS->>CSB: publish(CandleClosed) at replay timestamp
        CSB->>VC: Advance VirtualClock to event time
        CSB->>DEB: Dispatch event

        DEB->>GSS: Strategy evaluation
        GSS->>GSS: Process in virtual thread
        GSS->>DEB: SignalGenerated (if any)

        DEB->>EH: Signal → Order execution
        EH->>SIM: placeOrder() (simulated)
        SIM->>SIM: Match at candle close price
        SIM-->>EH: Simulated fill
        EH->>DEB: OrderFilled event

        CRS->>GW: Broadcast candle update
        GW->>FE: CandleReplayEvent
    end

    CRS->>GW: Broadcast "replay complete"
    GW->>FE: ReplayStatusEvent
    CRS->>VC: Switch back to LIVE mode
```

---

## 11. Risk Management Architecture

```mermaid
graph TB
    subgraph Entry["Order Entry Point"]
        SIG["SignalGenerated<br/>event"]
    end

    subgraph RiskChain["RiskCheckChain (Sequential, Short-Circuit)"]
        direction TB
        KC["KillSwitchRiskCheck<br/>Is kill switch engaged?"]
        DLC["DailyLossRiskCheck<br/>Daily PnL > max-daily-loss?"]
        PLC["PositionLimitRiskCheck<br/>Open positions > max-open-positions?"]
        MEC["MarginEnforcementHandler<br/>Sufficient margin?"]

        KC -->|PASS| DLC
        DLC -->|PASS| PLC
        PLC -->|PASS| MEC
    end

    subgraph KillSwitch["KillSwitchCoordinator"]
        KS_E["engage(reason)"]
        KS_D["disengage()"]
        KS_B["Broker kill switch API"]
    end

    subgraph Actions["Risk Actions"]
        SUPP["SignalSuppressed<br/>event"]
        EXEC["Proceed to<br/>OrderExecution"]
        HALT["ReconciliationHaltRequired<br/>event"]
    end

    SIG --> KC

    KC -->|REJECT| SUPP
    KC -->|"engaged →"| KS_E
    KS_E --> KS_B

    DLC -->|REJECT| SUPP
    PLC -->|REJECT| SUPP
    MEC -->|REJECT| SUPP
    MEC -->|PASS| EXEC

    style KC fill:#ff6b6b,color:#fff
    style SUPP fill:#ff6b6b,color:#fff
    style EXEC fill:#6bcb77,color:#fff
    style KS_E fill:#ffd93d
```

---

## 12. Data Storage Architecture

```mermaid
graph TB
    subgraph Sources["Data Sources"]
        BROKER_REST["Broker REST API<br/>(Historical Data)"]
        BROKER_WS["Broker WebSocket<br/>(Live Feeds)"]
        BROKER_OPT["Broker API<br/>(Option Chains)"]
    end

    subgraph Ingest["Ingestion Layer"]
        DJR["DownloadJobRegistry"]
        EQDP["EquityHistorical<br/>DownloadPlanner"]
        RODP["RollingOption<br/>DownloadPlanner"]
        IS["IncrementalSyncService"]
        GD["GapDetector"]
        BF["BackfillService"]
        CR["CandleResampler"]
    end

    subgraph Storage["Storage Layer"]
        direction TB
        subgraph Parquet["Parquet (Hive Partitioned)"]
            P_EQ["segment=NSE_EQ/<br/>symbol=X/<br/>data_0.parquet"]
            P_FNO["segment=NSE_FNO/<br/>symbol=X/<br/>data_0.parquet"]
            P_BSE["segment=BSE_EQ/<br/>symbol=X/<br/>data_0.parquet"]
        end

        subgraph DuckDB["DuckDB"]
            D_EVT["events table<br/>(Domain Events)"]
            D_SCAN["scans table<br/>(Scan Results)"]
            D_PIPE["pipeline_graphs table<br/>(Pipeline Definitions)"]
            D_ANALYTICS["Analytics Engine<br/>(SQL queries on Parquet)"]
        end

        subgraph Chronicle["Chronicle Queue"]
            CH_AUDIT["Audit Log Writer<br/>(All events persisted)"]
            CH_DLQ["Dead Letter Queue<br/>(Failed events)"]
            CH_WAL["Event WAL<br/>(Write-Ahead Log)"]
        end

        subgraph Memory["In-Memory"]
            IM_FEAT["Feature Store<br/>(OptionsAware)"]
            IM_STATE["InMemoryStateStore<br/>(Pipeline State)"]
        end
    end

    subgraph Consumers["Data Consumers"]
        REST_API["REST API<br/>(/api/v1/analytics)"]
        CLI_Q["CLI Queries"]
        STRAT_F["Strategy Feature<br/>Access"]
        REPLAY["Replay Engine"]
    end

    BROKER_REST --> DJR
    DJR --> EQDP
    DJR --> RODP
    EQDP --> IS
    RODP --> IS
    IS --> GD
    GD --> BF
    BF --> CR
    CR --> Parquet

    BROKER_WS --> D_EVT
    BROKER_OPT --> DuckDB

    Parquet --> D_ANALYTICS
    D_ANALYTICS --> REST_API
    D_ANALYTICS --> CLI_Q

    D_EVT --> CH_AUDIT
    D_EVT -.->|failures| CH_DLQ

    IM_FEAT --> STRAT_F
    IM_STATE --> REPLAY
```

---

## 13. CLI Execution Architecture

```mermaid
sequenceDiagram
    autonumber
    participant USER as User
    participant TC as TradeCli.main()
    participant CTX as CliContext
    participant IS as InteractiveShell<br/>(JLine3 REPL)
    participant CMD as CliCommand
    participant BF as BrokerSessionFactory
    participant BS as BrokerSession<br/>(DhanBrokerSession)
    participant FC as FullComposition
    participant BG as BrokerGateway
    participant BH as BrokerHandle

    USER->>TC: java -jar tradej-cli.jar
    TC->>CTX: Initialize CliContext
    CTX->>CTX: Load CliConfig, AliasStore, MacroStore
    CTX->>BF: Create BrokerSessionFactory

    alt Direct Mode (standalone)
        BF->>FC: FullComposition.createFull(profiles)
        FC->>BG: BrokerGateway.from(composition)
        BG->>BH: gateway.broker("dhan")
        BH-->>CTX: BrokerHandle ready
    else Attached Mode (to running app)
        CTX->>CTX: AttachClient → HTTP to localhost:8080
    end

    TC->>IS: Start InteractiveShell
    IS->>IS: JLine3 setup (history, tab completion)

    loop Command loop
        USER->>IS: "quote RELIANCE" (typed command)
        IS->>IS: Parse command + args
        IS->>CMD: CliMarketCommands.execute(["quote", "RELIANCE"])
        CMD->>BH: quote("RELIANCE")
        BH->>BH: BrokerCallSupport.timed()
        BH-->>CMD: GatewayResult<Quote>
        CMD->>CMD: OutputFormatter.format(quote)
        CMD-->>IS: Rendered output
        IS-->>USER: Formatted quote table
    end
```

---

## 14. Spring Boot Configuration Loading

```mermaid
graph TB
    subgraph Config["Application Configuration"]
        AY["application.yml<br/>(Base config)"]
        ADY["application-dev.yml<br/>(Dev profile)"]
        ADLY["application-dev-live.yml<br/>(Live dev)"]
        AUY["application-upstox-dev.yml"]
        PROP["config/dhan-local.properties<br/>(Secret tokens)"]
    end

    subgraph ConfigClasses["41 @Configuration Classes"]
        direction TB
        subgraph Broker["Broker Config"]
            DBC["DhanBrokerConfiguration"]
            IC["IciciConfiguration"]
            UC["UpstoxConfiguration"]
            BC2["BrokerConfiguration (deprecated)"]
        end

        subgraph Core["Core Config"]
            EBC["EventBusConfiguration"]
            RC["RuntimeConfiguration"]
            TC2["TimeConfiguration"]
            SC["SchedulingConfiguration"]
        end

        subgraph Trading["Trading Config"]
            STC["StrategyConfiguration"]
            RKC["RiskConfiguration"]
            SNC["ScanConfiguration"]
        end

        subgraph Data["Data Config"]
            PC["PersistenceConfiguration"]
            AC["AnalyticsConfiguration"]
            FSC["FeatureStoreConfiguration"]
        end

        subgraph GW["Gateway Config"]
            GWC["GatewayConfiguration"]
            GWSC["GatewayWebSocketConfig"]
            GWBC["GatewayBeansConfiguration"]
        end

        subgraph Pipeline["Pipeline Config"]
            PLC["PipelineConfiguration"]
        end

        subgraph Ops["Operations Config"]
            ALC["AlertConfiguration"]
            TRC["TracingConfiguration"]
            MC["MicrometerConfiguration"]
            HWC["GatewayHealthConfiguration"]
        end
    end

    AY --> ConfigClasses
    ADY --> ConfigClasses
    ADLY --> ConfigClasses
    AUY --> ConfigClasses
    PROP --> ConfigClasses

    subgraph Beans["Key Beans Created"]
        direction TB
        IBC_B["IBrokerConnection"]
        DEB_B["DisruptorEventBus"]
        GSS_B["GraphStrategySandbox"]
        PRH_B["PositionRiskHandler"]
        DAG_B["DagPipelineRuntimeService"]
        DES_B["DuckDbEventStore"]
        HC_B["BrokerHealthIndicator"]
        AM_B["AlertManager"]
    end

    ConfigClasses --> Beans
```

---

## 15. Scanner & Options Analytics Flow

```mermaid
sequenceDiagram
    autonumber
    participant CLI as CLI / Scheduler
    participant SC as ScanConfiguration
    participant PJS as ScanProfileJsonLoader
    participant SE as ScanEngine
    participant MPD as MarketDataProvider
    participant OLP as OptionLiquidity<br/>Criterion
    participant VSC as VolumeSpike<br/>Criterion
    participant PCC as PriceChange<br/>Criterion
    participant DEB as DisruptorEventBus
    participant GEB as GatewayEventBridge
    participant FE as Frontend

    CLI->>SC: Start scan profile
    SC->>PJS: Load scan-profiles.json
    PJS->>PJS: Parse universe (NSE_FNO, segments)
    PJS-->>SE: ScanProfile (criteria, universe)

    SE->>MPD: getLtpBatch(symbols)
    MPD-->>SE: LTP data
    SE->>MPD: getQuoteBatch(symbols)
    MPD-->>SE: Quote data (OHLCV)

    loop For each symbol in universe
        SE->>VSC: evaluate(symbol, quoteData)
        alt Volume spike detected
            VSC-->>SE: Hit (volume > 2x average)
        end

        SE->>PCC: evaluate(symbol, quoteData)
        alt Price change threshold met
            PCC-->>SE: Hit (change > 3%)
        end

        SE->>OLP: evaluate(symbol, optionData)
        alt Liquidity criteria met
            OLP-->>SE: Hit (OI > threshold, spread < max)
        end
    end

    SE->>SE: Rank and filter hits
    SE->>DEB: publish(ScanHitProduced) for each hit
    SE->>DEB: publish(ScanResultsPublished)

    DEB->>GEB: Dispatch
    GEB->>FE: Scan results via WebSocket

    Note over FE: Frontend renders scan dashboard
```

---

## 16. Broker Adapter Pattern (Hexagonal)

```mermaid
graph TB
    subgraph Ports["Ports (broker-api)"]
        direction TB
        IBC["IBrokerConnection<br/>(CapabilityMap)"]
        MPD["MarketDataProvider"]
        OC["OrderCommand"]
        OQ["OrderQuery"]
        PP["PortfolioProvider"]
        OP["OptionsProvider"]
        FP["FuturesProvider"]
        IR["InstrumentResolver"]
        WS["WebSocketMultiplexer"]
        MP["MarginProvider"]
        SRP["SessionRiskProvider"]
        CAP["ConditionalAlertProvider"]
        BOP["BracketOrderProvider"]
        GOP["GttOrderProvider"]
        SOC["SliceOrderCommand"]
        COP["CoverOrderProvider"]
        MSP["MarketStatusProvider"]
        NP["NewsProvider"]
    end

    subgraph Dhan["Dhan Adapters"]
        DM["DhanMarketDataProvider"]
        DO["DhanOrderCommandAdapter"]
        DOQ["DhanOrderQueryAdapter"]
        DP["DhanPortfolioProvider"]
        DOP["DhanOptionsProvider"]
        DFP["DhanFuturesProvider"]
        DIR["DhanInstrumentResolver"]
        DWS["DhanWebSocketMultiplexer"]
        DMP["DhanMarginProvider"]
        DSAP["DhanBracketOrderAdapter"]
        DGAP["DhanGttOrderAdapter"]
        DSOC["DhanSliceOrderAdapter"]
        DMSP["DhanMarketStatusProvider"]
    end

    subgraph Upstox["Upstox Adapters"]
        UM["UpstoxMarketDataProvider"]
        UO["UpstoxOrderCommandAdapter"]
        UOQ["UpstoxOrderQueryAdapter"]
        UP["UpstoxPortfolioProvider"]
        UOP["UpstoxOptionsProvider"]
        UFP["UpstoxFuturesProvider"]
        UIR["UpstoxInstrumentResolver"]
        UWS["UpstoxWebSocketMultiplexer"]
        UMP["UpstoxMarginProvider"]
        UGAP["UpstoxGttOrderAdapter"]
        USOC["UpstoxSliceOrderAdapter"]
        UCOP["UpstoxCoverOrderAdapter"]
        UMSP["UpstoxMarketStatusProvider"]
        UNP["UpstoxNewsProvider"]
    end

    subgraph ICICI["ICICI Adapters"]
        IM["IciciMarketDataProvider"]
        IO["IciciOrderCommandAdapter"]
        IOQ["IciciOrderQueryAdapter"]
        IP["IciciPortfolioProvider"]
        IOP["IciciOptionsProvider"]
        IFP["IciciFuturesProvider"]
        IIR["BreezeInstrumentResolver"]
        IWS["BreezeWebSocketMultiplexer"]
        IMP["IciciMarginProvider"]
        IBAP["IciciBracketOrderAdapter<br/>(synthetic)"]
        IGAP["IciciGttOrderAdapter<br/>(synthetic)"]
        ISOC["IciciSliceOrderAdapter<br/>(synthetic)"]
        ICOP["IciciCoverOrderAdapter<br/>(synthetic)"]
        IMSP["IciciMarketStatusProvider"]
    end

    IBC --> MPD
    IBC --> OC
    IBC --> OQ
    IBC --> PP
    IBC --> OP
    IBC --> FP
    IBC --> IR
    IBC --> WS
    IBC --> MP
    IBC --> SRP
    IBC --> CAP
    IBC --> BOP
    IBC --> GOP
    IBC --> SOC
    IBC --> COP
    IBC --> MSP
    IBC --> NP

    MPD -.-> DM
    MPD -.-> UM
    MPD -.-> IM

    OC -.-> DO
    OC -.-> UO
    OC -.-> IO

    BOP -.-> DSAP
    BOP -.->|"synthetic"| IBAP

    GOP -.-> DGAP
    GOP -.-> UGAP
    GOP -.->|"synthetic"| IGAP

    style IBAP fill:#ffcc80
    style IGAP fill:#ffcc80
    style ISOC fill:#ffcc80
    style ICOP fill:#ffcc80
```
