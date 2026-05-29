The governance class diagram is intentionally presented as a high-level overview. The complete `fos-governance-service` class structure is too large to fit in a single readable report diagram without losing clarity.

The overview groups the service into `config`, `identity`, `canonical`, `policy`, and `signal`. The `identity` module manages actor lifecycle and role-driven creation logic, `canonical` handles core domain entities such as players and teams, `policy` covers policy-context construction and OPA-based evaluation, and `signal` manages intake, processing pipeline, routing, and audit consumption. The `config` package centralizes application bootstrapping, security, policy wiring, signal pipeline wiring, and global exception handling.

Patterns highlighted in the overview:
- Factory Method
- Chain of Responsibility
- Port/Adapter
- Layered Controller → Service → Repository → Entity

Status: Draft pending user approval. Do not generate the final report version or detailed module diagrams until the user reviews and approves this overview.
