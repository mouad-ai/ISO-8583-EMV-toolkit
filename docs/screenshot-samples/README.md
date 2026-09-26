# Screenshot samples

Open this folder as a project in the sandbox IDE (`./gradlew runIde`). Everything here is made up
for screenshots: the PAN is the public test number 4111 1111 1111 1111, and the terminal, merchant,
cryptogram and dates are fake.

| File | Used for |
|---|---|
| `iso8583-authorization-request.hex` | Decode tab: ISO 8583:1987 ASCII 0100 with field 55 |
| `iso8583-authorization-response.hex` | Spare: the matching 0110 approval |
| `emv-field55.hex` | Field 55 on its own (EMV TLV mode) |
| `diff-expected.hex`, `diff-actual.hex` | Diff tab: amount, STAN, a missing field and a TVR bit differ |
| `payment-switch.log` | "Decode as ISO 8583" on a selection in the editor |
| `.cardwire/dialects/acme-acquirer.json` | Dialect autocomplete (valid processor override) |
| `.cardwire/dialects/broken-host.json` | Error banner (extends a misspelled dialect id) |

See [../screenshot-guide.md](../screenshot-guide.md) for the step-by-step guide.
