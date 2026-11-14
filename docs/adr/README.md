# Architecture decision records

Short records of the significant decisions in this project: the context, the decision, and its
consequences. Records are not edited after they are accepted; a later record supersedes an
earlier one by referencing it.

| # | Decision |
|---|---|
| [1](0001-record-architecture-decisions.md) | Record architecture decisions |
| [2](0002-postgresql-as-system-of-record.md) | PostgreSQL as the system of record, with database-enforced invariants |
| [3](0003-redis-lua-reservation-gate.md) | A Redis Lua script as the reservation gate |
| [4](0004-kafka-asynchronous-order-creation.md) | Kafka for asynchronous, idempotent order creation |
| [5](0005-database-sweep-for-payment-timeouts.md) | A database sweep for payment timeouts |
| [6](0006-two-tier-rate-limiting.md) | Two-tier rate limiting with Bucket4j |
| [7](0007-stateless-jwt-authentication.md) | Stateless JWT authentication |
| [8](0008-react-spa-instead-of-server-rendered-pages.md) | A React single-page app instead of server-rendered pages |
| [9](0009-load-test-inside-linux.md) | Load-test the containerized backend inside Linux |
