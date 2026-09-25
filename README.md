# Octet

Offline ISO 8583 and EMV TLV decoder for JetBrains IDEs (working name). See [SPEC.md](SPEC.md).

## Build

Requires JDK 21.

```
./gradlew check          # core unit tests + headless plugin tests
./gradlew runIde         # launch a sandbox IDE with the plugin
./gradlew verifyPlugin   # JetBrains Plugin Verifier
```

Modules: `core` (pure Kotlin, no IntelliJ dependencies) and `plugin` (IDE integration).
