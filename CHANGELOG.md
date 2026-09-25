# Changelog

## [Unreleased]

### M3 — Decode tool window (in progress)
- Input detection in `core`: hex (spaces, newlines, `0x`, commas allowed), base64, or raw ASCII, with a manual override.
- Hex dump layout in `core` with byte-offset ↔ text-position mapping.
- Tool window: input area, format selector, tree and hex view with two-way highlighting.
  Structured ISO 8583/TLV nodes, the dialect selector and the masking toggle follow once the M1/M2 core decoders land.

### M0 — Scaffold
- Gradle multi-module build: `core` (pure Kotlin/JVM, JUnit 5) and `plugin` (IntelliJ Platform Gradle Plugin 2.x).
- Empty "Octet" tool window built with Kotlin UI DSL v2.
- `NoNetworkTest` fails the build if `core` references `java.net`, `javax.net` or common HTTP client libraries.
- GitHub Actions CI: tests, `buildPlugin` and `verifyPlugin`.
