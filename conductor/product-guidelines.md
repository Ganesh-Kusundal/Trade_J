# Product Guidelines

## Prose Style
- **Clarity and Precision:** Use direct, technical language. Avoid ambiguity, especially regarding financial terms and order states.
- **Tone:** Professional, authoritative, and focused on reliability.
- **Terminology:** Stick to industry-standard trading and software engineering terminology.

## Design Principles
- **Performance First:** Every architectural decision should prioritize low latency and high throughput.
- **Reliability:** The system must handle failures gracefully, ensuring data integrity and correct order management.
- **Modularity:** Keep components loosely coupled to allow for easy extension (e.g., adding new brokers or strategies).
- **Auditability:** Ensure all actions (orders, data receipts) are logged and traceable for post-trade analysis.

## UX Principles (CLI/API focused)
- **Minimalist Feedback:** Provide clear, essential information in logs and CLI outputs.
- **Actionable Errors:** Error messages should clearly state the cause and suggest potential resolutions.
- **Consistency:** Ensure consistent naming conventions across APIs and CLI commands.
