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
- **Hex view masks whole values.** The tree follows SPEC 6.8 (PAN keeps first 6 + last 4), but the
  hex and ASCII columns hide every byte of a masked value, since partial masking of packed BCD
  nibbles would be confusing. The reveal toggle lives in the panel only and is never persisted.
- **Field 55 inside ISO 8583 is re-read by the M1 `EmvDecoder`** for formatted values and bits.
  In hex-text dialects each TLV byte is two characters in the message, so tag offsets are scaled
  by `valueLength / tlvBytes`; if the value is not clean hex, M2's raw children are shown instead.
- **ISO field masking by field number**: 2 keeps first 6 + last 4, 35 uses the track 2 rule, any
  other field or subfield the dialect marks sensitive is fully masked.
- **Pasted ISO text made only of hex digits is read as hex** by input auto-detection; pick "ASCII"
  as the input format to decode it as characters.
- **`DecodeView` is the tool window's single core entry point** (bytes + mode + reveal in, tree +
  masked ranges + problems out), so the Swing panel holds no decoding logic.

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
- **Not yet supported** (the loader says so): private TLV tags or lengths in an encoding
  different from the field's own character encoding. (`BITMAP` subfields arrived in M6.)
- **One JSON reader.** The dialect loader reuses the M1 reader (`emv/JsonResources`) rather than
  adding a second one.

## M6 — jPOS import

- **The class mapping is a data file** (`core/src/main/resources/octet/jpos/jpos-field-classes.json`)
  written from jPOS's public naming conventions: `IFA_` ASCII lengths and data (binary as hex
  text), `IFB_` BCD lengths with BCD numerics and raw binary (`H` = binary length), `IFE_` EBCDIC,
  `IF_CHAR`/`IF_ECHAR` fixed text, `*_AMOUNT` signed amounts, `*_BITMAP` bitmaps, `IF_NOP` skipped.
  jPOS `length` is taken as Octet `maxLength` (digits, characters or bytes as for Octet).
- **`pad` on BCD numeric classes**: `true` is `BCD_LEFT_PAD`; otherwise right padding with `0`,
  following jPOS's right-padded BCD. Other padding attributes are ignored.
- **Unmapped classes become raw binary fields** with the length type read from the class name
  (`LL`/`LLL`/`LLLL`) and a warning naming the class, as SPEC 7 asks.
- **Field 0 sets the MTI encoding, field 1 the bitmap encoding; a bitmap class on field 65 turns
  on the tertiary bitmap.** Fields 35/36 declared numeric are typed `z`, since they carry the
  track separator. Fields 2, 14, 34, 35, 36, 45 and 52 are marked sensitive. A binary field 55
  without a sub-field packager gets `BER_TLV` subfields.
- **Sub-field packagers**: a bitmap sub-field (or `emitBitmap="true"`) gives the `BITMAP` layout
  with the bitmap's `length` in bytes; fixed-length positional sub-fields give `FIXED`; BER-TLV
  packagers give `BER_TLV`. Other tagged packagers and variable-length positional sub-fields are
  left without subfields, with a warning.
- **`version` is set to 1987 with a warning**, since packagers don't say; framing is `NONE`.
- **The id comes from the file name** (`Acme Packager.xml` -> `acme-packager`); the IDE action adds
  `-2`, `-3`, ... rather than overwriting an existing dialect file.
- **XML safety**: the packager's DTD and all external entities are never loaded (entity resolver
  returns nothing, external DTD loading and entities disabled, secure processing on).
- **`BITMAP` subfields**: one fixed bitmap of `bitmapLength` bytes (binary, hex-ASCII or
  hex-EBCDIC), all bits numbering subfields (no continuation bit); each subfield is a full field
  definition with its own length prefix. Subfield errors are reported and keep the parent field.

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
## M4

- **Console hex detection threshold: 16 bytes**, written as contiguous hex digits or byte pairs
  separated by single spaces, not starting or ending inside a longer hex word. Shorter runs are
  mostly ids and hashes' prefixes; odd-length runs are skipped. The whole run becomes the link.
- **Console links keep the tool window's current mode and dialect**, since a log line does not say
  what it contains; the editor actions pick ISO 8583 or EMV TLV explicitly.
- **Settings are application-level** (`octet.xml` in the IDE config dir) and the console filter is
  checked per line, so toggling it applies to new output without restarting the run.

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

## Dialect picker (M3 + M5)
- `core/iso/DialectCatalog` merges built-ins with dialect file texts, so load order, labels and `extends` between user files are unit-tested without the IDE. The plugin only reads files from `DialectFileService`.
- A dialect file can extend another dialect file by `id`, not only a built-in. Duplicate ids: the first file wins as a base.
- Broken files are skipped with a status-line message instead of a popup, so a half-typed file being edited doesn't nag.
## M8 — Diff

- **Diff runs on the display tree (`DecodeNode`)**, not on decoder-specific models, so one diff covers
  ISO 8583 fields, subfields and EMV tags, and compares values exactly as the user sees them.
- **Matching**: siblings are matched by id; the n-th repeat of an id is matched with the n-th repeat
  on the other side (key `id#n`). Reordered elements count as unchanged. The result keeps the right
  side's order; a removed element is shown right after its left-side predecessor.
- **Counting**: added and removed subtrees count once at their top; a parent counts as changed only
  when its own value differs, so the summary reflects what the user would fix.
- **Masking**: both sides are decoded masked, so the tree and the copied report never contain clear
  sensitive values unless revealed upstream.
