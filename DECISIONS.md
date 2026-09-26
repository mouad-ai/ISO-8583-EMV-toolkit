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

## M2 — ISO 8583 core

- **Dialect file format follows the M5 schema** (`docs/dialects.md`): fields keyed by number,
  `extends` with property-by-property merge and `"remove": true`, `framing` entries by `type`.
  One addition: TPDU and HEADER entries take an optional `"prefix"` (`LENGTH_2_BINARY` or
  `LENGTH_4_ASCII`) for the common "length, then TPDU" framing.
- **Omitted `lengthEncoding`/`dataEncoding` mean ASCII.** The built-in files spell out every
  non-ASCII encoding so they validate against the schema without a defaults block.
- **Length units.** `maxLength` and length prefixes count digits/characters for text types and
  bytes for `b`. `b` data with ASCII or EBCDIC encoding is hex text, two characters per byte
  (jPOS `IFA_BINARY` style). BCD and binary prefixes use one byte for LL and two for LLL/LLLL.
- **"ASCII" data is read as ISO-8859-1 and EBCDIC as IBM037**, both of which map all 256 byte
  values, so any bytes decode and re-encode unchanged.
- **BCD padding.** `BCD` equals `BCD_LEFT_PAD`. Odd digit counts pad with `0` on the left, or `F`
  on the right for `BCD_RIGHT_PAD`; `padding.char` overrides the nibble. The pad nibble is not
  checked when decoding. Track data (`z`) in BCD maps `=` to nibble `D`.
- **`xn` amounts** (`C`/`D` sign then digits) count the sign in their length; in BCD the sign is
  one ASCII byte followed by packed digits.
- **Fixed-length padding when encoding**: numeric left with `0`, text right with space, or the
  field's `padding`; `b` and `xn` must be given at full length. Decoding keeps padding as-is.
- **Encoding validates `n`, `z`, `xn` and `b` content only.** `a`/`an`/`ans` are not checked, since
  real dialects routinely carry other characters in them.
- **Built-in 1993 dialect** has no field 65 (bit 65 announces the tertiary bitmap) and no fields
  above 128; its upper field definitions follow public summaries of ISO 8583:1993 and should be
  checked against your processor's spec. 1987 field 65 is `b 1`.
- **Framing detection** tries the dialect's framings in order and takes the first that decodes
  with no errors; otherwise the one whose first error is furthest into the message.
- **Error style**: "Field N (name): problem at offset 0xNN." Decoding stops at the first
  structural error in the message; subfield errors (private TLV, field 55) are reported but do not
  stop the message. A length prefix larger than `maxLength` is an error.
- **Field 55 children** come from the M1 `BerTlv` parser (`EmvBerTlvSubfieldDecoder`); their value
  is the raw tag value in hex, with names and sensitivity from the EMV tag dictionary. Formatting
  and bit meanings stay with `EmvDecoder`. The decoder is injectable for other TLV dialects.
- **Not yet supported** (the loader says so): `BITMAP` subfield layouts, and private TLV tags or
  lengths in an encoding different from the field's own character encoding.
- **One JSON reader.** The dialect loader reuses the M1 reader (`emv/JsonResources`) rather than
  adding a second one.

## M7 — Builder

- **Builder logic lives in `core/builder`.** `MessageBuilder` checks and encodes (via `IsoEncoder`),
  `TlvBuilder`/`EmvValueEncoder` build field 55, `Exporters` renders outputs. The Swing tab only
  moves values between the table and these classes.
- **Friendly EMV input.** The field 55 editor takes each tag's natural form (amount `10.00`, date
  `2026-09-25`, currency `MAD`, country `MA`, text) based on the M1 dictionary's display kind.
  `hex:` forces raw hex for any tag. Existing values are shown in the friendly form only when it
  encodes back to the exact same bytes; otherwise they appear as `hex:`.
- **Amounts use the currency row.** An amount's decimal places come from the 5F2A row's currency
  (ISO 4217 minor unit), else 2.
- **Exports are not masked.** SPEC 6.8 masks copies of decoded data, but the builder's output is a
  message the user typed in and needs verbatim, so building and copying it counts as the explicit
  override.
- **Header bytes.** Framings with a header (TPDU or custom) get a zero-filled header in the builder;
  editing header bytes can come later.
- **jPOS snippet** uses `ISOMsg.set(int, String)` for text fields and `ISOUtil.hex2byte` for type `b`
  fields. It names the dialect but does not generate a packager.
