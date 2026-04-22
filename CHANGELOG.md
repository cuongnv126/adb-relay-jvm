# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1] - 2026-04-22

### Added
- Initial public release of the ADB Relay JVM library.
- `AdbRelayServer` — standalone Ktor/Netty WebSocket relay server; configurable host and port, start/stop lifecycle.
- `adbRelayModule()` extension function to embed the relay into an existing Ktor `Application`.
- `RelayCoordinator` — thread-safe pairing of `dev` and `device` WebSocket clients by shared session token.
- `RelaySession` — per-connection handling: JSON handshake validation, pre-pairing binary frame buffer (32 MB cap), bidirectional forwarding after pairing.
- `Handshake` — kotlinx-serialization JSON model for the v1 handshake object (`v`, `role`, `token`).
- Rejects connections whose first frame is binary (close code 1003) or whose handshake is malformed.
- Published to Maven Central under `org.androidgradletools:adbrelay-jvm`.

[0.1.1]: https://github.com/cuongnv126/adb-relay-jvm/releases/tag/v0.1.1
