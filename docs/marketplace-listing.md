# Marketplace listing (draft)

Text for the JetBrains Marketplace page. The plugin description in `plugin.xml` uses the same
wording. Replace "Octet" once the final name has passed a trademark check, and keep "EMV" and
"ISO" out of the product name itself (they may be used descriptively).

## Short description

Decode, build and diff ISO 8583 messages and EMV TLV data inside your IDE, offline, with your own
message specs.

## Description

Payment developers read raw messages all day: hex dumps from logs, test tools and bug tickets,
ISO 8583 layouts that differ per processor, and field 55 blobs nobody can read by eye. Octet
decodes them where you already work, without sending card data to a website.

- **Offline and private**: no network calls, no telemetry. PANs and other sensitive values are
  masked by default, including in copies. Safe for PCI environments.
- **Your dialect**: start from a built-in ISO 8583 layout and describe only what your processor
  changes, with autocomplete and validation. Import existing jPOS packagers.
- **Where you are**: decode a selection in the editor or the Run console, or paste into the tool
  window. Selecting a field highlights its bytes, and clicking a byte selects its field.
- **EMV made readable**: tag names, amounts with currency, dates, and bit-by-bit TVR, TSI, AIP,
  CVM results, terminal capabilities and CID.
- **Build and diff (Pro)**: build a message from a form and export it as hex, base64, a `byte[]`
  literal or a jPOS snippet. Compare a request with its response, or expected with actual.

## Free and Pro

The free edition decodes with the built-in dialects and includes EMV decoding, masking and the
editor actions. Pro adds project and user dialects, jPOS import, the builder, the diff view and
console hex detection.

## Tags

Payments, ISO 8583, EMV, Fintech, Parser

## Screenshots to take (in a real IDE)

Step by step, with sample files: [screenshot-guide.md](screenshot-guide.md).

1. Decode tab: an ISO 8583 message with the tree and hex view side by side, a field selected.
2. Field 55 expanded, with the TVR bits shown.
3. A dialect file with autocomplete open on `lengthType`.
4. The diff tab comparing an expected and an actual request.
5. The builder with the code export output.
6. (Optional) The error banner on a broken dialect file.
7. (Optional) "Decode as ISO 8583" on a selection in a log file.

## Before publishing

- Create the paid plugin under the vendor account (Mouad EL MRABATE), and put the product code JetBrains
  assigns into `plugin.xml` (`product-descriptor`) and `OctetLicense.PRODUCT_CODE`, replacing
  the `POCTET` placeholder.
- Set `release-date` and `release-version` in the `product-descriptor` for each release.
- Choose the final name and update `plugin.xml`, the tool window title and this text.
