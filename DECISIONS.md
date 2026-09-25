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
