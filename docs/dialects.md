# Dialect files

A dialect describes one ISO 8583 message layout: how the MTI and bitmap are encoded, which framing
prefixes to try, and how each field is laid out. Cardwire ships generic built-in dialects; you add
your own for each processor.

## Where they go

| Scope   | Folder                                   | Use                                |
|---------|------------------------------------------|------------------------------------|
| Project | `.cardwire/dialects/*.json` in the project  | Shared with the team through git   |
| User    | `<IDE config dir>/cardwire/dialects/*.json` | Your own, available in every project |

Files in these folders get autocomplete, documentation and validation from the bundled schema
(`core/src/main/resources/cardwire/dialect/dialect.schema.json`). Cardwire picks up added, edited,
renamed and deleted files without a restart.

## A complete dialect

```json
{
  "id": "acme-base",
  "name": "Acme base layout",
  "version": "1987",
  "mti": { "encoding": "ASCII" },
  "bitmap": { "encoding": "HEX_ASCII" },
  "framing": [{ "type": "NONE" }, { "type": "LENGTH_2_BINARY" }],
  "fields": {
    "2":  { "name": "Primary account number", "type": "n", "lengthType": "LLVAR", "maxLength": 19,
            "lengthEncoding": "ASCII", "dataEncoding": "ASCII", "sensitive": true },
    "4":  { "name": "Amount, transaction", "type": "n", "lengthType": "FIXED", "maxLength": 12 },
    "55": { "name": "ICC data", "type": "b", "lengthType": "LLLVAR", "maxLength": 255,
            "subfields": { "layout": "BER_TLV" } }
  }
}
```

Fields are keyed by field number (2 to 192). A dialect without `extends` needs `version`, `mti`,
`bitmap`, and every field needs `name`, `type`, `lengthType` and `maxLength`.

- An omitted `lengthEncoding` or `dataEncoding` means `ASCII`.
- For type `b` with `ASCII` or `EBCDIC` data encoding, the value is hex text (2 characters per
  byte) and lengths count bytes.

## Framing

`framing` lists the prefixes to try, in order: `NONE`, `LENGTH_2_BINARY`, `LENGTH_4_ASCII`,
`TPDU` (5 bytes) or `HEADER` (with `length` in bytes). `TPDU` and `HEADER` can also take a
`prefix` of `LENGTH_2_BINARY` or `LENGTH_4_ASCII` for a length sent before them, as many binary
hosts do: `{ "type": "TPDU", "prefix": "LENGTH_2_BINARY" }`.

## Processor overrides

Most processor specs are a standard layout with a handful of differences. Instead of copying the
whole layout, name the base dialect in `extends` and list only what changes:

```json
{
  "id": "acme",
  "name": "Acme processor",
  "extends": "iso8583-1987-ascii",
  "fields": {
    "2":  { "maxLength": 16 },
    "48": { "remove": true },
    "63": { "name": "Acme private data", "type": "ans", "lengthType": "LLLVAR", "maxLength": 999,
            "subfields": { "layout": "PRIVATE_TLV", "tagLength": 2, "lengthLength": 3 } }
  }
}
```

- A field entry that the base also has is merged property by property: field 2 above keeps its
  base name, type and encodings and only gets a new `maxLength`.
- `"remove": true` drops a base field, and cannot be combined with other properties.
- A field the base does not have (63 above) must be complete.
- Top-level `mti`, `bitmap` and `framing` replace the base's value when present.
- A dialect can extend another override dialect.

## Subfield layouts

| `layout`      | For                                         | Settings                                             |
|---------------|---------------------------------------------|------------------------------------------------------|
| `FIXED`       | Fixed-position slices (e.g. field 90)       | `fields`: ordered `{ name, length }` slices, numbered from 1 |
| `BER_TLV`     | EMV data such as field 55                   | none                                                 |
| `PRIVATE_TLV` | Tag-length-value in fields 48, 62, 63, 126… | `tagLength`, `lengthLength`, encodings, tag names    |
| `BITMAP`      | A bitmap followed by the subfields it flags | `bitmapLength`, `bitmapEncoding` (default `BINARY`), numbered `fields` |

`PRIVATE_TLV` tags and lengths currently have to use the field's own character encoding; other
`tagEncoding`/`lengthEncoding` values are accepted by the schema but not decoded yet.
