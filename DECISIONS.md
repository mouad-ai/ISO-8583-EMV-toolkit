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
