# 0003: SQLite local storage

## Context
Rounds with raw events must be stored locally and queried across sessions. This is a single-user desktop app without a server. (MongoDB is the default elsewhere but does not fit an embedded desktop app.)

## Decision
Use SQLite via xerial JDBC in `%APPDATA%/AimTune`; settings as JSON (kotlinx.serialization). Whether to use Exposed or plain JDBC is decided when the schema is implemented (M3).

## Consequences
- Zero setup for users, a single file to back up.
- Raw events can be large; they are stored compactly and metrics can be recomputed from them.
- Schema migrations need a small versioning mechanism.
