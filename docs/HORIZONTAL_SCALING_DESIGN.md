# Trade-J Horizontal Scaling Design

## Overview

This document defines the architecture for scaling Trade-J from a single-process
deployment to a horizontally scalable, multi-node platform capable of supporting
multiple simultaneous users, brokers, and asset classes.

## Current Architecture (Single Node)

```
┌─────────────────────────────────────────────┐
│                Trade-J App                   │
│                                             │
│  Broker WS ──► Disruptor ──► Strategy       │
│                  Ring Buffer  ──► Execution  │
│                               ──► Scanner   │
│                                             │
│  DuckDB (in-process)                        │
│  Chronicle Queue (local files)              │
│  Caffeine Cache (in-memory)                 │
│                                             │
│  Spring Boot + WebSocket Gateway            │
└─────────────────────────────────────────────┘
```

**Limitations:**
- Single point of failure
- DuckDB is in-process (no shared state)
- Chronicle Queue is local (no cross-node replay)
- Ring buffer is in-memory (no cross-node events)
- Caffeine cache is per-JVM (no shared caching)

## Target Architecture (Multi-Node)

```
                    ┌──────────────┐
                    │  Load        │
  Users ──────────► │  Balancer    │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────▼─────┐ ┌───▼────┐ ┌────▼─────┐
        │  Node 1   │ │ Node 2 │ │  Node 3   │
        │  (NSE)    │ │ (BSE)  │ │  (MCX)    │
        └─────┬─────┘ └───┬────┘ └────┬─────┘
              │            │            │
              └────────────┼────────────┘
                           │
                    ┌──────▼───────┐
                    │   Event      │
                    │   Transport   │
                    │  (Kafka/      │
                    │   Redis)      │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────▼─────┐ ┌───▼────┐ ┌────▼─────┐
        │ Analytics │ │ Store  │ │  Replay   │
        │  Node     │ │ Node   │ │  Node     │
        └───────────┘ └────────┘ └───────────┘
```

## Event Transport Abstraction

### Interface

```java
public interface EventTransport {
    void publish(String topic, DomainEvent event);
    void subscribe(String topic, Consumer<DomainEvent> handler);
    void subscribe(String topic, String consumerGroup, Consumer<DomainEvent> handler);
    long replay(String topic, long fromOffset, Consumer<DomainEvent> handler);
}
```

### Implementations

| Transport | Use Case | Latency | Throughput |
|-----------|----------|---------|------------|
| `InMemoryEventTransport` | Single-node, testing | <1μs | 1M events/s |
| `KafkaEventTransport` | Production, durability | 1-10ms | 100K events/s |
| `RedisStreamsTransport` | Low-latency, moderate durability | <1ms | 500K events/s |

## Partition Strategy

### Symbol-Based Sharding

Events are partitioned by symbol to ensure all data for a given instrument
is processed by the same node:

```
partition = hash(symbol) % numPartitions

Node 1: partitions 0-3 (RELIANCE, TCS, INFY, ...)
Node 2: partitions 4-7 (SBIN, HDFCBANK, ICICIBANK, ...)
Node 3: partitions 8-11 (NIFTY, BANKNIFTY, ...)
```

### Shard Router

```java
public class SymbolShardRouter {
    private final int numShards;
    private final Map<Integer, EventTransport> shardTransports;

    public EventTransport route(String symbol) {
        int shard = Math.abs(symbol.hashCode()) % numShards;
        return shardTransports.get(shard);
    }
}
```

## Stateless Node Design

### Principles

1. **No local state** — All state stored in external systems
2. **Idempotent processing** — Events can be replayed safely
3. **Partition-aware** — Nodes only process their assigned partitions
4. **Health-checkable** — Each node exposes `/health` endpoint

### State Externalization

| Current (Local) | Target (External) |
|-----------------|-------------------|
| DuckDB | PostgreSQL / ClickHouse |
| Chronicle Queue | Kafka / Redis Streams |
| Caffeine Cache | Redis |
| In-memory OMS | Event-sourced OMS on Kafka |
| Local token files | Vault / AWS Secrets Manager |

## Node Types

### Market Data Node
- Connects to broker WebSocket feeds
- Publishes normalized events to event transport
- Stateless — can be replaced without data loss
- Scaled by exchange/segment

### Strategy Node
- Subscribes to market data topics
- Runs strategy logic
- Publishes signals to signal topic
- Stateless — signals are idempotent
- Scaled by symbol partition

### Execution Node
- Subscribes to signal topic
- Routes orders to broker REST API
- Publishes order events to order topic
- Requires idempotency cache (Redis)
- Scaled by broker

### Analytics Node
- Subscribes to all topics
- Computes indicators, analytics, dashboards
- Writes to external database
- Stateless computation
- Scaled by workload

### Gateway Node
- WebSocket server for frontend clients
- Subscribes to relevant topics
- Pushes real-time updates to clients
- Stateless — sessions in Redis
- Scaled by connection count

## Deployment Model

### Kubernetes

```yaml
# market-data-node (per exchange)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: market-data-nse
spec:
  replicas: 2  # HA
  template:
    spec:
      containers:
      - name: tradej-market-data
        env:
        - name: TRADEJ_EXCHANGE
          value: NSE
        - name: TRADEJ_EVENT_TRANSPORT
          value: kafka
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: kafka:9092
```

### Configuration

```yaml
tradej:
  mode: LIVE
  event-transport: kafka
  kafka:
    bootstrap-servers: kafka:9092
    topic-prefix: tradej
  partitioning:
    strategy: symbol-hash
    num-partitions: 12
  cache:
    type: redis
    host: redis:6379
  tokens:
    store-type: env
  alerts:
    webhook-url: https://hooks.slack.com/...
    cooldown-seconds: 300
```

## Migration Path

### Phase 1: Event Transport (Current → Kafka)
1. Implement `KafkaEventTransport`
2. Replace `DisruptorEventBus` publish path with transport publish
3. Keep Disruptor as local fan-out within each node
4. Chronicle Queue becomes the local WAL (already implemented)

### Phase 2: State Externalization
1. Replace DuckDB with PostgreSQL for historical queries
2. Replace Caffeine with Redis for shared caching
3. Replace local token files with env vars (already implemented)

### Phase 3: Node Specialization
1. Split app into market-data, strategy, execution, gateway nodes
2. Add symbol-based partitioning
3. Add load balancer in front of gateway nodes

### Phase 4: Multi-Exchange
1. Deploy market-data nodes per exchange (NSE, BSE, MCX)
2. Add exchange-aware routing
3. Scale strategy nodes by partition

## Failure Modes

| Failure | Impact | Recovery |
|---------|--------|----------|
| Market data node crash | No ticks for that partition | K8s restarts pod; Kafka replays from offset |
| Strategy node crash | No signals for that partition | K8s restarts; re-subscribes to Kafka |
| Execution node crash | Pending orders may be lost | Idempotency cache in Redis prevents duplicates |
| Kafka broker down | Events buffered locally | Chronicle WAL buffers until Kafka recovers |
| Redis down | Cache misses, token refresh fails | Graceful degradation to REST + file tokens |
