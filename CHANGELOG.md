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
### M2 — ISO 8583 core
- Dialect model and JSON loader in the dialect file format (fields keyed by number, `extends` overrides with `remove`), with path-named load errors.
- Built-in generic dialects: ISO 8583:1987 ASCII, ISO 8583:1987 binary, ISO 8583:1993 ASCII (with tertiary bitmap support).
- MTI in ASCII, BCD or EBCDIC with version/class/function/origin labels; primary, secondary and tertiary bitmaps in binary, hex-ASCII or hex-EBCDIC.
- FIXED, LLVAR, LLLVAR and LLLLVAR fields; ASCII, BCD, binary and EBCDIC length prefixes; ASCII, EBCDIC, BCD (left/right pad) and binary data; types n, a, an, ans, b, z, xn.
- Framing: none, 2-byte binary length, 4-byte ASCII length, TPDU or custom header (optionally after a length), with auto-detection.
- Subfields: fixed slices, private TLV with character tags, and BER-TLV for field 55 via the M1 parser.
- Best-effort decoding with byte offsets on every element and precise errors (e.g. "Field 35 (Track 2 data): LLVAR length 34 exceeds remaining 12 bytes at offset 0x4F.").
- Encoder that re-encodes decoded messages byte-identically; property-based round-trip, truncation and corruption tests.
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
