# Changelog

## [Unreleased]

### M1 — TLV core
- BER-TLV decoder and encoder with byte offsets, multi-byte tags, long-form lengths and byte-exact round trip.
- Best-effort parsing: malformed or truncated input returns the decoded part plus errors with offsets; unknown tags decode structurally.
- EMV tag dictionary (JSON resource) covering the SPEC minimum set and other common tags.
- Bit-level decoders for AIP (82), TVR (95), TSI (9B), Terminal Capabilities (9F33), CVM Results (9F34) and CID (9F27).
- Formatters for amounts (with currency and exponent), dates, times, ISO 4217 currencies and ISO 3166 countries.
- DOL parsing for PDOL, CDOL1/2, DDOL, TDOL and Log Format.
- Masking of PAN, track and cardholder name tags by default, with a reveal option.
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
