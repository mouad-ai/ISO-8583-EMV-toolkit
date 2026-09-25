# Changelog

## [Unreleased]

### M0 — Scaffold
- Gradle multi-module build: `core` (pure Kotlin/JVM, JUnit 5) and `plugin` (IntelliJ Platform Gradle Plugin 2.x).
- Empty "Octet" tool window built with Kotlin UI DSL v2.
- `NoNetworkTest` fails the build if `core` references `java.net`, `javax.net` or common HTTP client libraries.
- GitHub Actions CI: tests, `buildPlugin` and `verifyPlugin`.
