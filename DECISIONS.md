# Decisions

Choices made where SPEC.md leaves room, newest last.

## M0

- **IntelliJ baseline 2025.3 (build 253), no upper bound.** From 2025.3 JetBrains ships one unified
  IntelliJ IDEA distribution instead of separate Community and Ultimate builds, so building against
  `intellijIdea("2025.3")` covers both. `untilBuild` is left open; `verifyPlugin` checks the
  recommended IDE releases from 253 onward.
- **JVM 21, not 17.** IntelliJ Platform 2024.2+ requires Java 21, so both modules compile to JVM 21.
- **Kotlin API/language version 2.2.** The IDE supplies the Kotlin stdlib at runtime
  (`kotlin.stdlib.default.dependency=false`), and 2025.3 bundles Kotlin 2.2, so code must not call
  newer stdlib APIs. `core` declares the stdlib `compileOnly` for the same reason.
- **Plugin id `io.github.mouadai.octet`.** Reverse-DNS under the owner's GitHub namespace; the
  product name "Octet" stays a placeholder per SPEC.
- **No-network guard.** `NoNetworkTest` scans both the compiled classes (constant pool strings) and
  the sources of `core` for networking package prefixes, so it also catches fully-qualified uses
  that never appear in an import.
- **`buildSearchableOptions` disabled** until there is a settings page (M5+), to keep builds fast.
- **`runIde` in CI.** CI cannot open an IDE window, so `OctetToolWindowTest` boots a headless IDE
  with the plugin loaded and checks the tool window is registered and its panel builds.

## M1

- **EMV code lives in `io.github.mouadai.octet.core.emv`**, data files in `core/src/main/resources/octet/emv/`
  (`emv-tags.json`, `emv-bitfields.json`, `iso4217.json`, `iso3166.json`).
- **No JSON library.** `core` has no third-party dependencies (the IDE supplies only the Kotlin
  stdlib), so a small strict JSON reader (`JsonResources`) loads the bundled data files.
- **Byte-exact round trip.** Decoded nodes keep their original length bytes, so a non-minimal long
  form such as `81 06` re-encodes unchanged. Nodes built in code get the minimal form.
- **`00` padding between TLV objects is skipped** (EMV Book 3 Annex B allows it). It is not kept,
  so padded input does not round-trip byte for byte. `FF` is not treated as padding.
- **Partial parses.** Decoding stops at the first structural error and returns everything before
  it plus a `TlvError` with an absolute offset. A value that runs past the end is kept as a
  truncated node (`isTruncated`), so the UI can still show and highlight it. Nesting is capped at
  32 levels. Lengths use at most 4 bytes; indefinite length (`80`) is rejected.
- **Amounts** use the exponent from `5F36` when present, else the ISO 4217 minor unit of `5F2A`,
  else 2. Dates `YY` 00-49 are 20xx and 50-99 are 19xx (EMV Book 4).
- **RFU bits that are set are reported** as "RFU bit set" rather than ignored, since they usually
  mean a kernel- or scheme-specific meaning the generic dictionary does not know. AIP byte 2 is
  labelled kernel specific for the same reason.
- **Masking** follows SPEC 6.8: `5A` keeps first 6 and last 4, `57` applies the PAN rule up to the
  `D` separator and hides the rest, `56`, `5F20`, `9F0B`, `9F1F`, `9F20` and `99` are fully
  hidden. The raw hex is masked the same way so the hex view cannot leak it.
- **Reference tables were generated** from the public ISO 4217/3166 code lists (the `pycountry`
  data set for codes and names, the JDK's `java.util.Currency` for minor units). Funds and metal
  codes without a minor unit get exponent 0.
- **9F6E and 9F53 are scheme specific.** They are named generically and shown as hex until a
  scheme-aware decoder exists.
## M3

- **Input auto-detection order: hex, then base64, then raw ASCII.** Text valid as both hex and
  base64 (e.g. `AAAA`) is read as hex, the common case for pasted dumps. Hex cleanup strips
  whitespace, `0x` prefixes and commas, so `byte[]` literals paste directly. Odd-length hex falls
  through to the next format in auto mode and is an explicit error when hex is forced.
- **Raw ASCII input is taken byte-for-byte as ISO-8859-1**, falling back to UTF-8 only when the
  text has characters above U+00FF.
- **`DecodeNode` in `core`.** The tool window shows a display-neutral tree (id, name, value,
  offset, length). TLV and ISO 8583 results are adapted into it, which keeps the UI independent of
  the M1/M2 model types and keeps the offset lookup unit-testable.

## M5 — Dialect authoring (schema, folders, overrides)

- **Fields are a map keyed by field number** (`"fields": {"2": {...}}`), not an array. It makes
  per-field overrides natural and lets the schema reject duplicate or out-of-range numbers (2-192).
- **Processor overrides via `extends`.** A dialect names a base dialect and lists only the fields
  it changes; entries are merged property by property, `"remove": true` drops a field, and a field
  new to the base must be complete. The schema switches between complete and partial field rules
  on whether `extends` is present (draft-07 `if`/`then`).
- **Schema checks what JSON Schema can**: enums, required properties, field-number range, LLVAR
  ≤ 99 and LLLVAR ≤ 999, `HEADER` framing needs a length. Cross-file checks (unknown `extends`
  id, cycles) belong to the loader.
- **Schema lives in `core` resources** (`octet/dialect/dialect.schema.json`) so core tests can
  check the built-in dialects against it; the plugin maps it onto dialect files.
- **JSON plugin is an optional dependency.** Schema registration sits in `octet-json.xml`, so
  Octet still loads in IDEs without the JSON plugin, just without dialect autocomplete.
- **Folders**: project `.octet/dialects/*.json` (under the project dir and any content root, not
  recursive) and user `<config dir>/octet/dialects/*.json`. A project-level VFS listener publishes
  `DialectsChangedListener.TOPIC` once per batch of changes touching those folders.
- **YAML dialect files** are not mapped yet; JSON only until the loader supports YAML.
