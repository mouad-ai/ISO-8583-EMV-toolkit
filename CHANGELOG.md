# Changelog

## [Unreleased]

### M9 — Release prep
- Marketplace freemium licensing isolated in `plugin/licensing`: `OctetLicense` (paid-feature checks, register dialog) and `LicenseStampVerifier` (JetBrains license stamp verification, following JetBrains' official sample). Paid features stay unlocked in `runIde` and tests.
- `plugin.xml` declares an optional paid `product-descriptor` (placeholder code `POCTET` until the vendor account exists).
- Sample dialects in `samples/dialects`: an ASCII acquirer override and a binary host with TPDU framing.
- Dialects that `extends` another may omit `fields` (framing-only overrides validate).
- README rewritten; Marketplace listing draft in `docs/marketplace-listing.md`.
- Vendor set to Mouad EL MRABATE; plugin version 1.0.0 so it matches the product descriptor's release-version.
- Screenshot kit: sample files in `docs/screenshot-samples` (open as a project in `runIde`) and a step-by-step `docs/screenshot-guide.md`.
- Paid features check the license: the Build and Diff tabs show an "Enter license" note when unlicensed, project and user dialects are not offered, jPOS import opens the register dialog, and console hex detection stays off.
- "Diff" tab in the tool window, using the same dialects as the decoder and reloading with them.
- Dialect files show a banner with their load error, or a note when dialects need Octet Pro; banners refresh when any dialect file changes.

### M8 — Diff
- `MessageDiff` in `core`: structural diff of two decoded messages (ISO 8583 fields or EMV tags), matched by id, with repeated tags matched by occurrence and reordering tolerated.
- `DiffReport`: plain-text list of added, removed and changed elements with their paths.
- `OctetDiffPanel`: paste two messages, see one merged tree with changes highlighted, copy the report. Decodes both sides the same way (ISO 8583 with a dialect, or EMV TLV), masked.

### M7 — Builder
- "Build" tab in the tool window: pick a dialect, MTI and framing, fill fields in a table, build, and copy the output.
- Field values are checked per field with messages that name the field; a, an and ans character classes are enforced.
- Field 55 editor: one row per EMV tag, values typed as amounts, dates, currencies, countries or text (or `hex:`), built into BER-TLV.
- Exports: hex, base64, Java `byte[]` literal, Kotlin `byteArrayOf` literal and a jPOS `ISOMsg` snippet.
### M4 — Editor and console actions
- "Octet > Decode as ISO 8583" and "Decode as EMV TLV" in the editor and Run/Debug console context menus decode the selection in the tool window.
- Optional console filter (Settings > Tools > Octet, off by default) turns hex dumps of 16+ bytes, contiguous or space-separated, into links that open them in the decoder.
### M5 — Dialect authoring (in progress)
- JSON Schema for dialect files, including processor overrides (`extends`, partial fields, `remove`).
- Autocomplete and validation for `.octet/dialects/*.json` and user dialect files (needs the bundled JSON plugin).
- Project and user dialect folders are listed and watched; changes publish a `DialectsChangedListener` event.
- `docs/dialects.md` describes the format.
- The decode window's dialect picker lists project and user dialects after the built-ins, labelled by scope, and reloads when a dialect file changes. A dialect file may extend a built-in or another dialect file; files that fail to load are skipped and named in the status line.

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
### M6 — jPOS import
- Import jPOS `GenericPackager` XML into an Octet dialect (Tools | Import jPOS Packager as Octet Dialect), written to `.octet/dialects/<id>.json` and opened in the editor.
- jPOS field-class mapping table as a resource (`octet/jpos/jpos-field-classes.json`): IFA_, IFB_, IFE_, IF_CHAR/IF_ECHAR, bitmaps, amounts, `pad` handling; unmapped classes become raw binary fields with a warning.
- Sub-field packagers: bitmap-driven (`emitBitmap`) to the new `BITMAP` layout, fixed positional to `FIXED`, BER-TLV to `BER_TLV`; field 55 as binary defaults to BER-TLV.
- Bitmap-driven subfields (`"layout": "BITMAP"`) now decode, with offsets and per-subfield errors such as "Field 127.3 (Routing info): ...".
- The importer never loads the packager's DTD or any external entity.

### M3 — Decode tool window
- Input detection in `core`: hex (spaces, newlines, `0x`, commas allowed), base64, or raw ASCII, with a manual override.
- Hex dump layout in `core` with byte-offset ↔ text-position mapping.
- Tool window: input area, format selector, tree and hex view with two-way highlighting.
- "Decode as EMV TLV" (default) shows every tag with name, formatted value, raw hex, set bits and DOL entries, fully expanded.
- Sensitive tags are masked in the tree and in the hex view; a session-only "Reveal sensitive values" toggle shows them.
- TLV parse errors are shown under the input with their offset.
- "Decode as ISO 8583" (default) with a dialect selector (the three built-in dialects) and a framing selector (auto by default): framing, MTI with its meaning, bitmap, and every field with subfields.
- Field 55 inside an ISO message expands into formatted EMV tags located at their exact bytes, in both binary and hex-text dialects.
- PAN (first 6 + last 4), track 2 and other sensitive fields are masked in the tree and hex view.

### M0 — Scaffold
- Gradle multi-module build: `core` (pure Kotlin/JVM, JUnit 5) and `plugin` (IntelliJ Platform Gradle Plugin 2.x).
- Empty "Octet" tool window built with Kotlin UI DSL v2.
- `NoNetworkTest` fails the build if `core` references `java.net`, `javax.net` or common HTTP client libraries.
- GitHub Actions CI: tests, `buildPlugin` and `verifyPlugin`.
