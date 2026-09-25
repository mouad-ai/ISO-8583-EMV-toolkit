# SPEC — ISO 8583 / EMV Toolkit for JetBrains IDEs

> Working name: **Octet** (placeholder — check JetBrains Marketplace and trademarks before publishing).
> Do NOT put "EMV" or "ISO" in the product name itself (EMV is an EMVCo trademark); using them descriptively in the plugin description is fine.

This file is the source of truth for Claude Code. Read it fully before writing code. Work milestone by milestone (section 9), and do not start a milestone until the previous one's acceptance criteria pass.

---

## 1. Problem

Developers working on card payments (issuing, acquiring, switches, HSM integrations, terminal/POS backends) constantly deal with raw messages:

- hex dumps from logs, Wireshark captures, test tools, or bug tickets;
- ISO 8583 messages whose layout differs per processor/network ("dialects");
- field 55 (ICC data), a BER-TLV blob of EMV tags that nobody can read by eye;
- bitmaps, BCD vs ASCII vs EBCDIC encodings, LL/LLL length prefixes.

Today they copy the hex into a random website (a PCI DSS problem: real PANs leave the machine), a standalone desktop tool, or write throwaway parsing code. Nothing does this properly **inside the IDE, offline, with their own message spec**.

## 2. Target users

- Java/Kotlin developers at banks, processors, fintechs, payment switches, terminal vendors.
- QA engineers writing payment test cases.
- Primary buyer: the employer (team licenses). Secondary: individual consultants.

## 3. Positioning

"Decode, build and diff ISO 8583 and EMV data without leaving your IDE — 100% offline, with your own message specs."

Key selling points, in order:
1. **Offline and private**: no network calls, no telemetry, PAN masking by default. Safe for PCI environments.
2. **Your dialect**: define or import (jPOS packager XML) the exact spec of your processor.
3. **Where you already are**: decode directly from the editor, console, or a log file.

## 4. Scope

### In scope (v1)
- ISO 8583 decode/encode driven by dialect files (1987, 1993, 2003 layouts).
- EMV BER-TLV decode (field 55 and standalone), with a tag dictionary and bit-level breakdown of key bitfield tags.
- Tool window, editor/console actions, message builder, message diff.
- Dialect files (JSON/YAML) with schema-based autocomplete; jPOS GenericPackager XML import.
- Freemium licensing via JetBrains Marketplace.

### Out of scope (v1)
- Any network connectivity (no sending messages to hosts).
- Cryptography: no PIN block decryption, no MAC/ARQC computation, no key handling. (Possible later paid add-on; keep the architecture open to it.)
- Shipping any processor's proprietary spec. Built-in dialects must be generic and derived from public knowledge only.

## 5. Architecture

Gradle multi-module project:

```
octet/
├─ core/          Pure Kotlin/JVM library. NO IntelliJ dependencies.
│                 Parsing, encoding, dialect model, TLV, masking, diff.
├─ plugin/        IntelliJ Platform plugin. UI, actions, settings, licensing.
│                 Depends on :core.
└─ SPEC.md
```

Rules:
- `core` must be fully unit-testable with plain JUnit 5; it is where 70%+ of the logic lives.
- `plugin` only adapts `core` to the IDE. No parsing logic in UI classes.
- Kotlin, JVM target 17 (or whatever the chosen IntelliJ baseline requires — check).
- Build with the **IntelliJ Platform Gradle Plugin 2.x**. Target a recent stable IntelliJ baseline (check current docs for the right `sinceBuild`); support both IntelliJ IDEA Community and Ultimate. Only depend on `com.intellij.modules.platform` (+ `java` only if strictly needed) so it can also run in other JetBrains IDEs.
- UI: Kotlin UI DSL v2, tool window registered in `plugin.xml`.
- Zero network code anywhere. Add a test that fails if `java.net`/HTTP clients are used in `core`.

## 6. Domain model (core)

### 6.1 Messages
```
IsoMessage(
  header: ByteArray?,          // optional framing header / TPDU
  mti: Mti,
  fields: SortedMap<Int, FieldValue>,
  raw: ByteArray               // original bytes, for offset mapping
)
FieldValue(id, rawBytes, decoded: String, offset: Int, length: Int, children: List<FieldValue>)
```
Every decoded element must keep its **byte offset and length** in the original buffer. This powers hex highlighting in the UI and precise error messages.

### 6.2 MTI
4 digits: version, class, function, origin. Decode each position into a human label (e.g. `0100` = 1987 / Authorization / Request / Acquirer). Encoding per dialect (ASCII, BCD, EBCDIC).

### 6.3 Bitmap
- Primary bitmap (64 bits); bit 1 set ⇒ secondary bitmap present; bit 65 set ⇒ tertiary (when the dialect allows it).
- Representation per dialect: binary (8 bytes) or hex-ASCII (16 chars) or EBCDIC hex.

### 6.4 Field definition (from dialect)
```
FieldSpec(
  id: Int,
  name: String,
  type: n | a | an | ans | b | z | xn | custom,
  lengthType: FIXED | LLVAR | LLLVAR | LLLLVAR,
  maxLength: Int,              // in characters/digits/bytes depending on type
  lengthEncoding: ASCII | BCD | BINARY | EBCDIC,
  dataEncoding: ASCII | BCD | BCD_LEFT_PAD | BCD_RIGHT_PAD | EBCDIC | BINARY,
  padding: optional rules,
  sensitive: Boolean,          // PAN, track data, PIN block, CVV… masked in UI
  subfields: SubfieldLayout?   // fixed-position subfields, nested TLV, or bitmap-driven subfields
)
```
Subfield layouts must support at least: fixed-position slices, BER-TLV (field 55), and private TLV/"tag-length-value with ASCII tags" variants common in fields 48, 62, 63, 126, 127.

### 6.5 Framing
Optional message prefix, configurable per dialect: none, 2-byte binary length, 4-byte ASCII length, TPDU (5 bytes), custom header of N bytes. Auto-detection tries the configured options and picks the one that parses cleanly.

### 6.6 EMV BER-TLV
- Tag: first byte; if low 5 bits are `11111`, subsequent bytes follow while bit 8 is set. Bit 6 (`0x20`) of the first byte = constructed ⇒ recurse.
- Length: short form (< 0x80) or long form (`0x81 xx`, `0x82 xx xx`).
- Tag dictionary (name, format, source, description) built from the **public EMV Books (EMVCo, free download)**. Minimum set: 4F, 50, 57, 5A, 5F20, 5F24, 5F2A, 5F34, 82, 84, 8A, 8E, 91, 95, 9A, 9B, 9C, 9F02, 9F03, 9F07, 9F09, 9F0D-9F0F, 9F10, 9F12, 9F1A, 9F1E, 9F21, 9F26, 9F27, 9F33, 9F34, 9F35, 9F36, 9F37, 9F41, 9F53, 9F6E. Dictionary lives in a resource file (JSON), not in code.
- Bit-level decoders for: 82 (AIP), 95 (TVR), 9B (TSI), 9F33 (Terminal Capabilities), 9F34 (CVM Results), 9F27 (CID). Show each set bit with its meaning.
- Value formatters: amounts (n12, with currency from 5F2A when present), dates (YYMMDD), currency/country codes (ISO 4217 / 3166 numeric tables as resources).
- Parse DOLs (PDOL/CDOL lists: tag + length pairs, no values).
- Unknown tags must still decode structurally (tag / length / raw value) — never fail the whole parse on an unknown tag.

### 6.7 Error handling
Parsing is **best-effort and partial**: decode as far as possible, then return a structured error, e.g.:

> Field 35 (Track 2): LLVAR length 63 exceeds remaining 12 bytes at offset 0x4A.

Never throw raw exceptions to the UI.

### 6.8 Masking
Fields/tags flagged `sensitive` (default: fields 2, 14, 35, 36, 45, 52, 55-tag 57/5A/5F20, 9F1F…) are shown masked (first 6 + last 4 for PAN, full mask otherwise). A toggle reveals clear values for the current session only. Masking also applies to copy actions unless explicitly overridden.

## 7. Dialects

- Format: JSON or YAML, one file per dialect, with a published **JSON Schema** registered via `JsonSchemaProviderFactory` so users get autocomplete and validation when editing dialect files.
- Locations: bundled (read-only), project-level (`.octet/dialects/*.json`, shareable via git), and user-level (IDE config dir).
- Built-in generic dialects:
  - ISO 8583:1987 ASCII (ASCII MTI, hex-ASCII bitmap, ASCII lengths)
  - ISO 8583:1987 binary (BCD numerics, binary bitmap, BCD lengths)
  - ISO 8583:1993 ASCII
- **jPOS import**: parse jPOS `GenericPackager` XML (`<isopackager><isofield id=".." length=".." name=".." class="org.jpos.iso.IFA_LLNUM"/>…`) and map jPOS field classes (IFA_*, IFB_*, IFE_*, IF_CHAR, *_BITMAP, *_LLNUM, *_LLLCHAR, *_BINARY, etc.) to our `FieldSpec`. Unmapped classes produce a warning and fall back to a raw-bytes field. This is a major adoption feature since many shops already have jPOS packagers.

## 8. Features (plugin)

### 8.1 Decode tool window
- Input area: paste hex (with or without spaces/newlines/`0x`), base64, or raw ASCII. Auto-detect input format.
- Dialect selector + framing selector (auto by default).
- Output: tree (MTI → bitmap → fields → subfields/TLV) with name, decoded value, raw hex.
- Hex view side panel; selecting a tree node highlights its bytes and vice versa.
- Copy node value, copy as JSON, copy full decoded message as text report (masked).

### 8.2 Editor, console and log integration
- Action "Decode as ISO 8583" / "Decode as EMV TLV" on selected text in any editor or in the Run/Debug console; opens result in the tool window.
- Optional console filter that detects long hex runs and adds a clickable "decode" link (off by default, settings toggle).

### 8.3 Builder
- Form driven by the dialect: pick MTI, enable fields, fill values (with validation per type/length).
- Field 55 sub-editor for TLV tags.
- Output: hex, base64, Java/Kotlin `byte[]` literal, and a jPOS `ISOMsg` code snippet.

### 8.4 Diff
- Compare two messages (e.g. request vs response, expected vs actual in a failing test): side-by-side tree with added/removed/changed fields and TLV tags highlighted.

### 8.5 Settings
- Default dialect, masking on/off default, console filter toggle, custom dialect folders.

## 9. Milestones (do them in order)

**M0 — Scaffold**
Multi-module Gradle project, IntelliJ Platform Gradle Plugin 2.x, empty tool window, `./gradlew runIde` and `./gradlew verifyPlugin` pass, CI (GitHub Actions) runs tests + verifier.

**M1 — TLV core**
BER-TLV parser/encoder, tag dictionary resource, bit-level decoders (AIP, TVR, TSI, 9F33, CVM results, CID), formatters, masking.
Acceptance: unit tests with hand-built samples; round-trip `encode(decode(x)) == x`; unknown tags handled; malformed lengths produce partial results + error with offset.

**M2 — ISO 8583 core**
Dialect model + JSON loader, MTI, bitmaps (primary/secondary/tertiary), all length and data encodings, framing detection, subfields, offsets.
Acceptance: the 3 built-in dialects decode and re-encode hand-crafted sample messages byte-identically; property-based round-trip tests; precise error messages on truncated/corrupted input.

**M3 — Decode tool window**
Paste → tree + hex view with bidirectional highlighting; field 55 auto-expanded as TLV; masking toggle.

**M4 — Editor/console actions**
Decode-selection actions; optional console hex-detection filter.

**M5 — Dialect authoring**
JSON Schema for dialect files, project/user dialect folders, reload on change, validation errors shown in editor.

**M6 — jPOS import**
Import GenericPackager XML → dialect JSON; mapping table for jPOS field classes; tests with representative packager files written by us.

**M7 — Builder**
Dialect-driven form, TLV sub-editor, export formats (hex, base64, byte[] literal, jPOS snippet).

**M8 — Diff**
Two-message structural diff view.

**M9 — Licensing & release**
Freemium via JetBrains Marketplace paid-plugin licensing (read the current Marketplace docs on paid plugins and `LicensingFacade`). Isolate the license check in one service. Marketplace listing assets, README, CHANGELOG, sample dialects.

## 10. Free vs paid split

| Free | Paid |
|---|---|
| Decode with built-in generic dialects | Custom project/user dialects |
| EMV TLV decode + tag dictionary | jPOS packager import |
| Masking | Builder + code export |
| Decode-selection action | Diff view |
| | Console auto-detection filter |

The free tier must be genuinely useful (it is the marketing). Paid features are what a team with its own processor spec needs daily.

## 11. Test data rules

- All test messages are **hand-crafted** from public knowledge. Never use real card data, real captured traffic, or any employer/processor documents.
- Use test PANs from well-known public test ranges only, and clearly fake values elsewhere.
- Sample TLV to start with:
  - `9F02 06 000000001000` → Amount Authorised = 10.00
  - `5F2A 02 0504` → Transaction Currency = 504 (MAD)
  - `9A 03 260925` → Transaction Date = 2026-09-25
  - `95 05 0000000000` → TVR, no bits set

## 12. Conventions for Claude Code

- Write tests first for everything in `core`.
- Keep parsing pure and deterministic; no global state.
- Resources (tag dictionary, currency/country tables, built-in dialects) are data files, not Kotlin constants.
- No network, no telemetry, no analytics — ever.
- After each milestone: run all tests, `verifyPlugin`, update CHANGELOG, and stop to summarize what was done and what is next.
- When a spec detail is ambiguous (e.g. an obscure encoding variant), implement the most common behavior, make it configurable in the dialect, and note it in `DECISIONS.md`.
