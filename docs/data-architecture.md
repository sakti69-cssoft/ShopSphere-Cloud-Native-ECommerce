# ShopSphere data architecture

Each service owns its data and schema. Services never read another service's database directly.

| Service | Store | Owned data | Integrity and concurrency |
| --- | --- | --- | --- |
| Auth | MySQL 8.4 | users and credentials | unique email, Flyway, JPA validation |
| Product | MongoDB 8 | catalog documents | unique SKU/slug, category/brand indexes, native pagination |
| Inventory | MySQL 8.4 | stock and reservations | pessimistic row lock plus JPA version |
| Cart | Redis 7.4 | short-lived cart aggregate | JSON serialization, namespaced key, configurable TTL |
| Order | MySQL 8.4 | orders, items, shipping snapshots | unique order number, normalized items, Flyway |

MySQL migrations live in each owning service under `src/main/resources/db/migration`. Hibernate uses `ddl-auto=validate`, so it cannot mutate schemas. Mongo indexes are declared on the document. Redis keys use `shopsphere:cart:{userId}` and expire after `CART_TTL_HOURS`.

Compose creates separate databases and least-scope users from environment variables. Passwords are not stored in source; `.env.example` has placeholders only. Named volumes preserve state across container recreation.

## Local lifecycle

1. Copy `.env.example` to `.env` and replace every placeholder.
2. Start infrastructure with `docker compose --profile test up -d`.
3. Start everything with `docker compose --profile local up --build -d`.
4. Check `docker compose ps` and `docker compose logs <service>`.
5. Use `docker compose down` to stop without deleting data. Add `--volumes` only for an intentional reset.

Testcontainers dependencies are isolated to test scope and use disposable containers rather than development volumes.
