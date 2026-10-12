# 0003: SQLite local storage

## Context
Rounds with raw mouse events must be stored locally and queried across sessions. This is a single-user desktop app without a server, so an embedded database fits better than MongoDB (the default elsewhere). The data is relational (sessions, rounds, events, metrics) and the access pattern is small.

## Decision
- SQLite through xerial `sqlite-jdbc` with plain JDBC; the schema is small and fixed, so Exposed would add a dependency without removing real work.
- One connection behind a lock, WAL mode, foreign keys on. Writes happen on a background thread.
- A round's raw mouse reports are stored as one compact blob per round (delta + zigzag varint, deflated) rather than one row each: a 45 s round at 8 kHz would otherwise be hundreds of thousands of rows. Target events (a few per round) and derived metrics are normal rows.
- Metrics are derived data: recomputable from the raw blob, stored with a `metrics_version`.
- Migrations are an ordered list applied from `PRAGMA user_version`; released entries are never edited, and a newer database is refused instead of being downgraded.
- Settings are a separate JSON file (kotlinx.serialization), written atomically; a corrupt file is reported, not replaced.

## Consequences
- Zero setup for users, a single file to back up, small files.
- Raw events are not queryable with SQL; analysis loads and decodes a round (fast, the whole round is needed anyway).
- Changing a metric definition means bumping `METRICS_VERSION` and recomputing from the stored raw data.
- Schema changes need a new migration entry and a test.
