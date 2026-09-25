# Changelog

## [Unreleased]

### M5 — Dialect authoring (in progress)
- JSON Schema for dialect files, including processor overrides (`extends`, partial fields, `remove`).
- Autocomplete and validation for `.octet/dialects/*.json` and user dialect files (needs the bundled JSON plugin).
- Project and user dialect folders are listed and watched; changes publish a `DialectsChangedListener` event.
- `docs/dialects.md` describes the format.

### M0 — Scaffold
- Gradle multi-module build: `core` (pure Kotlin/JVM, JUnit 5) and `plugin` (IntelliJ Platform Gradle Plugin 2.x).
- Empty "Octet" tool window built with Kotlin UI DSL v2.
- `NoNetworkTest` fails the build if `core` references `java.net`, `javax.net` or common HTTP client libraries.
- GitHub Actions CI: tests, `buildPlugin` and `verifyPlugin`.
