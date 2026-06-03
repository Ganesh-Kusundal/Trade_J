# Implementation Plan [checkpoint: aa800e1]

## Phase 1: Full Session Soak Tests
- [x] Task: Write harness to poll and log JMX/Micrometer metrics (heap, GC, threads, ring buffer depth) during live sessions (177b4f7)
- [x] Task: Execute NSE full session soak test and archive logs (177b4f7)
- [x] Task: Execute MCX full session soak test and archive logs (177b4f7)
- [x] Task: Conductor - User Manual Verification 'Phase 1: Full Session Soak Tests' (Protocol in workflow.md) (62f032b)

## Phase 2: Tick Reconciliation
- [ ] Task: Build tick counter utility to reconcile broker-reported ticks against system-processed `MarketTickEvent`s
- [ ] Task: Execute tick reconciliation test for NIFTY, BANKNIFTY, RELIANCE, SBIN, MCX GOLD over a live session
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Tick Reconciliation' (Protocol in workflow.md)

## Phase 3: Order Book Validation
- [ ] Task: Build test harness to inject packet drops, delays, and out-of-order frames into websocket stream
- [ ] Task: Execute order book validation for L2, DEPTH_20, and DEPTH_200 feeds
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Order Book Validation' (Protocol in workflow.md)

## Phase 4: Option Chain Scale Testing
- [ ] Task: Build script to simultaneously subscribe to full chains for NIFTY, BANKNIFTY, FINNIFTY, and MIDCPNIFTY
- [ ] Task: Execute option chain scale test on live expiry day (or simulated burst) and measure event lag
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Option Chain Scale Testing' (Protocol in workflow.md)

## Phase 5: Disruptor Saturation Testing
- [ ] Task: Build saturation harness to inject 100k, 500k, 1M, and 2M ticks/min directly into Disruptor
- [ ] Task: Execute saturation tests and record consumer lag, loss, and ring utilization limits
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Disruptor Saturation Testing' (Protocol in workflow.md)

## Phase 6: Broker Failure Testing
- [ ] Task: Build chaos engineering scripts to simulate disconnects, token expiry, internet failure, and 429 rate limits
- [ ] Task: Execute failure tests against live/sandbox endpoints and measure exact recovery times
- [ ] Task: Conductor - User Manual Verification 'Phase 6: Broker Failure Testing' (Protocol in workflow.md)

## Phase 7: Multi-Broker Streaming Validation
- [ ] Task: Execute concurrent streaming test using Dhan, Upstox, and ICICI multiplexers and measure resource isolation
- [ ] Task: Execute intentional single-broker failure test and document auto-migration behavior (or lack thereof)
- [ ] Task: Conductor - User Manual Verification 'Phase 7: Multi-Broker Streaming Validation' (Protocol in workflow.md)

## Phase 8: Production Readiness Report
- [ ] Task: Compile collected metrics, limits, and failure modes into a final evidence-based markdown report
- [ ] Task: Conductor - User Manual Verification 'Phase 8: Production Readiness Report' (Protocol in workflow.md)
