# TRADE_J PRODUCTION READINESS — EXECUTION PLAN
## Agile Iterative Plan Prepared by Dr. Venkat Subramaniam, Principal Engineer

> *"The best architectures, requirements, and designs emerge from self-organizing teams. But they also emerge from iterative progress — working software over comprehensive documentation, responding to change over following a plan."*

**Core Philosophy:** Every phase must produce **working, tested, deployed software**. No phase is "done" until it passes both automated validation and peer review. We progress in thin vertical slices, not horizontal layers.

---

## TEAM STRUCTURE (Scrum-of-Scrums)

| Squad | Focus | Members |
|-------|-------|---------|
| **OMS Squad** | Order lifecycle, fill processing, position updates | Backend engineer + QA |
| **Broker Squad** | Dhan/Upstox/ICICI adapters, kill switch, token mgmt | Backend engineer + OMS specialist |
| **Risk Squad** | Kill switch, circuit breaker, MTM, reconciliation | Platform engineer + SRE |
| **Frontend Squad** | LiveTerminal, dashboards, SSE streaming | Frontend engineer + Platform engineer |
| **Strategy Squad** | Strategy validation, backtesting, paper trading | Quant engineer + Backend engineer |
| **SRE Squad** | Deployment, monitoring, alerting, HTTPS | SRE + Platform engineer |

Each squad operates in 1-week sprints. Cross-squad dependencies are resolved in a weekly Scrum-of-Scrums.

---

## RELEASE TRAIN (PI — Program Increment = 8 weeks)

### **SPRINT 0 — Foundation Hardening (Week 1)**
*"Make the foundation unbreakable before we build on it."*

**Sprint Goal:** All pre-existing test failures resolved. No green test is left red.

**Sprint Backlog:**

| ID | Story | Owner | Acceptance Criteria | Review |
|----|-------|-------|---------------------|--------|
| S0-1 | Add `@Tag("live")` assumption guards to `CheckSpeedLiveTest` | Risk Squad | All tests skip gracefully when no live broker; CI green | Venkat: "Show me the green build" |
| S0-2 | Add `@Tag("live")` assumption guards to `GatewayCheckSpeedLiveTest` | Risk Squad | All tests skip gracefully when no live broker; CI green | Venkat: "Defensive design over assumptions" |
| S0-3 | Investigate & fix `ConfigFileCountArchitectureTest` | OMS Squad | Architecture test passes; rule reflects current state or config is reduced | Venkat: "Tests document intent, not punish reality" |
| S0-4 | Add unit test for `ReadModelStore.putOrder` covering all 8 OrderView fields | OMS Squad | Test passes; all fields verified from `Order` domain model | Venkat: "If it's worth expanding, it's worth testing" |
| S0-5 | Add `triggerPricePaisa` to `OrderView` for SL/SL-M order display | Frontend Squad | 9th field added; UI displays trigger price for stop orders | Venkat: "Complete data, not partial" |

**Sprint 0 Review Checklist (Venkat):**
- [ ] All 4 modules' CI is green
- [ ] No new test failures introduced
- [ ] All PRs reviewed by 2 engineers
- [ ] No new TODOs/FIXMEs in production code

---

### **SPRINT 1 — Broker Safety Boundaries (Week 2)**
*"Every boundary should fail fast, fail loud, and fail safe."*

**Sprint Goal:** No broker can violate risk invariants. ICICI safely excluded from automated trading.

**Sprint Backlog:**

| ID | Story | Owner | Acceptance Criteria | Review |
|----|-------|-------|---------------------|--------|
| S1-1 | **BLOCKER-1**: Add broker capability enforcement gate | Broker Squad | OMS rejects MARKET/BRACKET orders when ICICI is active broker; clear error message | Venkat: "Express constraints in code, not in docs" |
| S1-2 | Frontend: Disable MARKET/BRACKET buttons when ICICI selected | Frontend Squad | UI reflects broker capabilities in real-time; graceful degradation | Venkat: "UI is the contract with the user" |
| S1-3 | **BLOCKER-2**: Document Upstox kill switch limitation | Broker Squad | ADR (Architecture Decision Record) published; platform-level kill switch test added | Venkat: "Make the invisible visible" |
| S1-4 | Add Upstox-specific position squaring on platform kill switch | Broker Squad | When platform kill switch fires, all Upstox positions are squared off via API | Venkat: "Defense in depth" |
| S1-5 | Add broker capability unit tests for all 3 brokers | Broker Squad | Tests verify MARKET/BRACKET/kill switch support matrix | Venkat: "Tests as living documentation" |

**Sprint 1 Demo (End of Week 2):**
- Show ICICI attempting to place MARKET order → rejected at OMS layer
- Show Upstox kill switch → all positions squared off within 5 seconds
- Show broker capability matrix in admin dashboard

---

### **SPRINT 2 — Strategy Validation Pipeline (Weeks 3-4)**
*"A strategy is not a strategy until it's survived the gauntlet."*

**Sprint Goal:** First production-validated strategy ready for paper trading. No real money until Sprint 4.

**Sprint Backlog:**

| ID | Story | Owner | Acceptance Criteria | Review |
|----|-------|-------|---------------------|--------|
| S2-1 | **BLOCKER-3**: Backtest framework for chosen strategy | Strategy Squad | Run 1+ year of historical data; output Sharpe, max drawdown, win rate, profit factor | Venkat: "Show me the equity curve" |
| S2-2 | Look-ahead bias audit of chosen strategy | Strategy Squad | No future data access verified by code review + property-based tests | Venkat: "Trust but verify" |
| S2-3 | Survivorship bias mitigation | Strategy Squad | Use point-in-time Nifty 500 constituent list; document any lookback | Venkat: "The past is a different country" |
| S2-4 | Replay-mode validation of strategy | Strategy Squad | Strategy produces same signals in replay as in backtest on identical data | Venkat: "Determinism is a feature" |
| S2-5 | Strategy performance dashboard | Frontend Squad | Real-time equity curve, drawdown chart, signal log in dashboard | Venkat: "What gets measured gets managed" |
| S2-6 | Strategy configuration YAML schema validation | Strategy Squad | Invalid configs rejected at startup; clear error messages | Venkat: "Fail fast at the boundary" |

**Sprint 2 Quant Review (Venkat, as institutional quant):**
- [ ] Sharpe ratio > 1.0 (minimum acceptable)
- [ ] Max drawdown < 15%
- [ ] Win rate documented; profit factor > 1.2
- [ ] > 1000 trades in backtest sample
- [ ] No regime bias (test across bull/bear/sideways periods)

---

### **SPRINT 3 — Production Deployment Infrastructure (Weeks 5-6)**
*"Deployment is not a phase. It is a capability."*

**Sprint Goal:** System can be deployed, monitored, and rolled back in under 15 minutes.

**Sprint Backlog:**

| ID | Story | Owner | Acceptance Criteria | Review |
|----|-------|-------|---------------------|--------|
| S3-1 | **HIGH-2**: HTTPS/TLS termination via reverse proxy | SRE Squad | Production config mandates HTTPS; HTTP redirects to HTTPS; HSTS enabled | Venkat: "Security is not optional" |
| S3-2 | **MED-4**: Dockerfile + docker-compose.yml | SRE Squad | Multi-stage build; image < 500MB; health checks; non-root user | Venkat: "Containers are cattle, not pets" |
| S3-3 | CI/CD pipeline (GitHub Actions) | SRE Squad | PR → tests → build → staging deploy; production deploy is manual approval | Venkat: "Automate the boring, manual the critical" |
| S3-4 | Deployment runbook | SRE Squad | Step-by-step guide with rollback procedure; verified by dry-run | Venkat: "If it's not documented, it doesn't exist" |
| S3-5 | Prometheus + Grafana dashboards | SRE Squad | All key metrics visible: order rate, fill rate, kill switch state, circuit breaker states | Venkat: "You can't manage what you can't see" |
| S3-6 | Alert routing (PagerDuty/Slack) | SRE Squad | Critical alerts page on-call; warnings go to Slack; escalation policy defined | Venkat: "Alerts should be actionable, not noisy" |

**Sprint 3 SRE Review (Venkat, as SRE):**
- [ ] MTTR (Mean Time To Recovery) < 15 minutes
- [ ] Zero-downtime deploys verified
- [ ] Rollback tested in staging
- [ ] On-call rotation defined
- [ ] Runbook reviewed by 2 engineers

---

### **SPRINT 4 — Paper Trading Validation (Weeks 7-8)**
*"Paper trading is the dress rehearsal. No excuses on opening night."*

**Sprint Goal:** Strategy runs in paper trading for 2+ weeks with real market data. No manual intervention required.

**Sprint Backlog:**

| ID | Story | Owner | Acceptance Criteria | Review |
|----|-------|-------|---------------------|--------|
| S4-1 | Paper trading deployment to staging | SRE Squad | System runs continuously for 2 weeks; zero crashes; zero manual restarts | Venkat: "If it needs babysitting, it's not ready" |
| S4-2 | Signal-to-fill latency measurement | OMS Squad | P99 latency < 500ms from signal to broker acknowledgment | Venkat: "Speed matters in trading" |
| S4-3 | Reconciliation monitoring | Risk Squad | Zero reconciliation halts during paper trading; tolerance never breached | Venkat: "Trust the reconciliation" |
| S4-4 | Kill switch drill | SRE Squad | Monthly kill switch drill: engage, verify all orders blocked, verify positions squared, disengage | Venkat: "Drill like it's real" |
| S4-5 | Failure injection testing | SRE Squad | Simulate broker disconnect, network failure, exchange delay; verify recovery within 30 seconds | Venkat: "Chaos engineering prevents production chaos" |
| S4-6 | Paper trading performance report | Strategy Squad | Compare paper trading metrics to backtest; document any deviations > 10% | Venkat: "Reality is the ultimate backtest" |

**Sprint 4 Gate Review (Venkat, as Principal Engineer):**
- [ ] Paper trading Sharpe within 10% of backtest
- [ ] Zero unplanned downtime
- [ ] All failure scenarios recovered automatically
- [ ] Kill switch drill passed
- [ ] On-call team trained and confident

---

### **SPRINT 5 — Controlled Live Deployment (Weeks 9-10)**
*"Start small. Scale with confidence."*

**Sprint Goal:** First real-money trades with minimum position size. Gradual ramp-up.

**Sprint Backlog:**

| ID | Story | Owner | Acceptance Criteria | Review |
|----|-------|-------|---------------------|--------|
| S5-1 | **FINAL GATE**: Production deployment with 1% of target capital | SRE Squad | System live with real money; max 1% capital at risk per trade | Venkat: "Earn the right to scale" |
| S5-2 | Daily trading journal | Strategy Squad | Every trade documented: signal reason, fill price, slippage, P&L | Venkat: "What gets written gets remembered" |
| S5-3 | Weekly performance review | All Squads | Compare live vs. backtest vs. paper; adjust if drift > 20% | Venkat: "Trust the process, verify the results" |
| S5-4 | Gradual capital ramp (1% → 5% → 10% → 25% → 50% → 100%) | SRE Squad | Each step requires 1 week of stable performance before proceeding | Venkat: "Patience compounds" |
| S5-5 | Post-trade reconciliation audit | Risk Squad | Every trade reconciled with broker within 5 minutes of fill | Venkat: "Every paisa accounted for" |

**Sprint 5 Final Review (Venkat, as Principal Engineer):**
- [ ] 4 weeks of live trading with no manual intervention
- [ ] Sharpe ratio within 15% of backtest
- [ ] Zero reconciliation failures
- [ ] Zero kill switch false positives
- [ ] All on-call alerts acknowledged within SLA
- [ ] Stakeholder sign-off obtained

---

## AGILE CEREMONIES (per sprint)

| Ceremony | Duration | Frequency | Participants | Venkat's Rule |
|----------|----------|-----------|--------------|---------------|
| **Sprint Planning** | 2 hours | Day 1 of sprint | All squads | "No story without acceptance criteria" |
| **Daily Standup** | 15 min | Daily | Squad members | "Yesterday, today, blockers. Nothing else." |
| **Sprint Review** | 1 hour | Last day of sprint | All squads + stakeholders | "Demo working software. No slides." |
| **Sprint Retrospective** | 1 hour | Last day of sprint | Squad members | "What worked, what didn't, what we'll change" |
| **Backlog Refinement** | 1 hour | Mid-sprint | All squads | "Estimate in story points, not hours" |
| **Architecture Review** | 30 min | Per PR | 2 senior engineers | "Review the diff, not the author" |

---

## DEFINITION OF DONE (per story)

A story is **DONE** when:
- [ ] Code is written and follows existing project conventions
- [ ] Unit tests are written and pass
- [ ] Integration tests pass (if applicable)
- [ ] TypeScript typecheck passes (for frontend stories)
- [ ] Backend compilation passes with JVM 21 (for backend stories)
- [ ] Code is reviewed by at least 1 senior engineer
- [ ] No new TODOs/FIXMEs in production code
- [ ] Documentation is updated (if public API changed)
- [ ] CI pipeline is green
- [ ] Deployed to staging and verified
- [ ] Stakeholder has seen the demo

---

## RISK REGISTER (per sprint)

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Strategy underperforms in live trading | Medium | High | Start with 1% capital; gradual ramp; weekly review |
| Broker API changes | Medium | High | Abstract broker layer; integration tests against sandbox |
| Market regime change | High | Medium | Strategy must be regime-agnostic; monitor for drift |
| Team burnout | Medium | High | Sustainable pace; no heroics; pair programming |
| Deployment failure | Low | Critical | Blue-green deploy; automated rollback; staging-first |
| Regulatory change | Low | Critical | Legal review before Sprint 5; compliance checklist |

---

## VENKAT'S REVIEW PRINCIPLES (applied at every review)

1. **"Show me the tests, not the code."** — If there are no tests, the code doesn't exist.
2. **"If it's not in CI, it didn't happen."** — Every PR must be green.
3. **"The best code is the code you don't write."** — Question every line.
4. **"Naming is the hardest problem in computer science."** — Spend 50% of coding time on naming.
5. **"Make the invisible visible."** — Logging, metrics, tracing. If you can't see it, you can't fix it.
6. **"Fail fast, fail loud, fail safe."** — Defensive design over optimistic assumptions.
7. **"The customer doesn't care about your architecture."** — Working software over comprehensive documentation.
8. **"If you can't explain it to a 5-year-old, you don't understand it."** — Complexity is the enemy.
9. **"Trust the process, verify the results."** — Process exists for a reason, but data overrides opinions.
10. **"Every system is a distributed system."** — Assume failure; design for recovery.

---

## SUCCESS METRICS (per sprint)

| Metric | Target | Measurement |
|--------|--------|-------------|
| Sprint velocity | 25-30 story points per squad | Jira/Linear |
| Defect escape rate | < 5% | Production incidents per release |
| Mean Time To Recovery | < 15 minutes | Incident logs |
| Test coverage | > 80% | JaCoCo |
| Code review turnaround | < 4 hours | GitHub PR metrics |
| Deployment frequency | 2+ per week | CI/CD pipeline |
| Change failure rate | < 10% | Production rollback count |

---

## VENKAT'S FINAL WORDS

> *"We don't ship code. We ship confidence. The code is the vehicle; confidence is the cargo. Every test we write, every review we do, every metric we collect — these are not bureaucracy. These are the cargo straps that ensure our confidence arrives intact."*
>
> *"In algorithmic trading, the cost of being wrong is measured in money. The cost of being slow is measured in opportunity. The cost of being careless is measured in careers. We have a responsibility to our capital, to our team, and to ourselves to do this right."*
>
> *"Start with Sprint 0. Make the foundation unbreakable. Then build. Iteratively. Incrementally. With working software at every step."*
>
> *"Jai Hind. Let's go build something remarkable."*

---

*Plan prepared by Dr. Venkat Subramaniam, Principal Engineer*
*Methodology: Agile, Iterative, Working-Software-First*
*Review cycle: Weekly sprint reviews, bi-weekly architecture reviews, monthly stakeholder demos*
