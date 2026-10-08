# cities-storage

Cities-owned SQLite adapter. The current schema is v10 and contains:

- `cities` — progression stage, treasury amount, revision and founder identity.
- `city_members` — one current city per player, persisted role id and last-known display name.
- `city_chunks` — a unique `(world_uuid, chunk_x, chunk_z)` key so a chunk cannot belong to two cities.
- `city_operations` — stable operation UUID and payload fingerprint for durable city mutations and treasury deposits.
- `city_treasury_entries` — idempotent journal rows owned by Cities.
- `city_role_audit` — role assignments and automatic ruler succession.
- `city_upgrades` — persistent city upgrades.
- `city_treaties` and `city_diplomacy_events` — bilateral treaty state and the diplomatic event journal.
- `city_treasury_transfers` — transfer idempotency records for atomic intercity payments.
- `city_market_listings`, `city_market_purchases` and `city_market_deliveries` — staged item escrow, atomic city-treasury settlement, sale history and durable buyer/refund mailboxes.
- `city_laws` and `city_law_audit` — persisted PvP, outsider block interaction, fire and explosion rules with an audit trail.
- `city_tax_policy` and `city_tax_policy_audit` — disabled-by-default daily per-resident tax, scheduling and policy audit.

Initialization uses a transactional, numbered SQLite migration (`PRAGMA user_version`). The connection enables WAL, FULL synchronous durability,
foreign keys and a bounded busy timeout. A database from a future schema version fails closed; the plugin must not overwrite or silently downgrade it.

`CityRepository` lives in `cities-core`. `SqliteCityRepository` owns JDBC and maps each read to an immutable `CityView` inside one consistent read transaction.
Queries run on the single Cities I/O executor, not a Bukkit event handler. Connections are short-lived; the executor belongs to the plugin lifecycle.
No SQLite connection, row object or repository is published through a public API.

Migrations v3-v10 add role audit, city upgrades, diplomacy, atomic treasury transfers, the city market, audited OP-only test adjustments, city laws and city taxes. Treaty transitions and their journal entries share one transaction; transfers and market purchases commit balances and ledger entries together.
Do not write directly to these tables from GUI, admin commands or another plugin. Add domain transactions through core ports before enabling any mutation.
