# Octet

Decode, build and diff ISO 8583 messages and EMV data without leaving your JetBrains IDE. Octet
works fully offline, with your own message specs. ("Octet" is a working name.)

- **Offline and private.** No network calls and no telemetry. PANs, track data and other
  sensitive values are masked by default, including in copies.
- **Your dialect.** Describe your processor's layout in a small JSON file, starting from a
  built-in layout and changing only what differs, or import an existing jPOS packager.
- **Where you already are.** Decode a selection in the editor or the Run console, or paste a hex
  dump into the Octet tool window.

## Features

| Feature | Free | Pro |
|---|---|---|
| Decode ISO 8583 with the built-in 1987 ASCII, 1987 binary and 1993 ASCII dialects | ✓ | ✓ |
| Decode EMV BER-TLV (field 55 and standalone), with tag names and bit-level TVR, AIP, CVM results… | ✓ | ✓ |
| Masking of sensitive values | ✓ | ✓ |
| "Decode as ISO 8583 / EMV TLV" on an editor or console selection | ✓ | ✓ |
| Project and user dialects, with autocomplete and validation | | ✓ |
| jPOS GenericPackager import | | ✓ |
| Message builder and export (hex, base64, `byte[]`, jPOS snippet) | | ✓ |
| Structural diff of two messages | | ✓ |
| Console hex detection with decode links | | ✓ |

## Dialects

Built-in dialects are generic and follow the public ISO 8583 layouts. For your processor, add a
file under `.octet/dialects/` in your project (shared through git) or in your user dialect folder:

```json
{
  "id": "my-processor",
  "name": "My processor",
  "extends": "iso8583-1987-ascii",
  "fields": {
    "2": { "maxLength": 16 },
    "48": { "remove": true }
  }
}
```

The format, overrides and subfield layouts are described in [docs/dialects.md](docs/dialects.md).
Ready-to-copy examples are in [samples/dialects](samples/dialects). The JSON Schema behind editor
autocomplete is `core/src/main/resources/octet/dialect/dialect.schema.json`.

## Build

Requires JDK 21.

```
./gradlew check          # core unit tests + headless plugin tests
./gradlew runIde         # sandbox IDE with the plugin (paid features unlocked)
./gradlew verifyPlugin   # JetBrains Plugin Verifier
./gradlew buildPlugin    # plugin zip in plugin/build/distributions
```

Modules: `core` (pure Kotlin, no IntelliJ dependencies: parsing, encoding, dialects, TLV, diff)
and `plugin` (IDE integration). See [SPEC.md](SPEC.md) for the product spec and
[DECISIONS.md](DECISIONS.md) for choices made along the way.

## Test data

All sample messages are hand-made from public specifications, with test PANs from public test
ranges. Never paste real card data into issues or tests.
