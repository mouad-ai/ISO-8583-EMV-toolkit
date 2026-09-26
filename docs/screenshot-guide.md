# Screenshot guide

Checklist for the JetBrains Marketplace screenshots listed in
[marketplace-listing.md](marketplace-listing.md). All inputs are in
[screenshot-samples](screenshot-samples).

## Before you start

1. **JDK 21** on your machine (`java -version`). Nothing else to install: Gradle downloads the IDE.
2. **Launch the sandbox IDE** from the repository root:

   ```
   ./gradlew runIde
   ```

   The first run downloads IntelliJ IDEA 2025.3 (a few GB) and takes a while. On Windows use
   `gradlew.bat runIde`. Paid features are unlocked in the sandbox (`-Dcardwire.license.dev=true` is set
   by the build), so no license is needed.
3. In the sandbox IDE, **File > Open** the folder `docs/screenshot-samples` and trust it.
4. **Theme**: use one theme for every shot. Settings > Appearance & Behavior > Appearance >
   Theme: **Light** (reads best on the Marketplace page, which is white). If you prefer dark, use
   Dark for all of them.
5. **Legibility**: View > Appearance > Zoom IDE to 110% or 125%. Turn off Presentation Mode and
   distraction-free mode. Close the Project tool window unless the shot needs it.
6. **Window size**: make the IDE window exactly **1200 × 760 px** (the size recommended for
   Marketplace screenshots; the upload page shows the current requirement, so check it there).
   On macOS a window-sizing tool (Rectangle, Moom) or `Window > Zoom` then resize helps; on Windows
   use PowerToys FancyZones or a fixed layout. Capture the window only, not the desktop.
   On a HiDPI/Retina screen the capture is 2400 × 1520: that is fine, the Marketplace scales it.
7. **Cardwire tool window**: it opens on the right (View > Tool Windows > Cardwire). Drag its edge so it
   takes about 60% of the width. Masking stays on (the default); never tick "Reveal" for a shot.

## 1. Decode tab: message, tree and hex view

1. Open `iso8583-authorization-request.hex` and copy its content.
2. Cardwire tool window > **Decode** tab: paste into the input, Input **Auto**, Decode as **ISO 8583**,
   Dialect **ISO 8583:1987 ASCII**, Framing **Auto**. Click **Decode**.
3. In the tree, click **DE 43 Card acceptor name/location** so its bytes are highlighted in the hex view.
4. In frame: the input (top), the tree with MTI, bitmap, DE 2 masked as `411111******1111`, and the
   hex view with the highlight.

## 2. Field 55 with TVR bits

1. Same message as shot 1. In the tree, expand **DE 55 ICC data (EMV)**, then
   **95 Terminal Verification Results** (shows "Transaction exceeds floor limit") and
   **9F27 Cryptogram Information Data** (ARQC).
2. Select **9F02 Amount, Authorised** (reads "15.00 USD") so its bytes are highlighted.
3. Alternative: paste `emv-field55.hex` with Decode as **EMV TLV** for a tree with only the tags.
4. In frame: the EMV tags with names and formatted values; scroll so 9F02 to 9F33 are visible.

## 3. Dialect file with autocomplete

1. Open `.cardwire/dialects/acme-acquirer.json`.
2. Put the caret inside field `"63"` after `"lengthType": ` , delete the value and press
   **Ctrl+Space** (macOS: **⌃Space**) to show the list (FIXED, LLVAR, LLLVAR...).
3. In frame: the file with `extends`, the partial field overrides and the completion popup. Keep
   the Cardwire tool window closed for this one.

## 4. Diff tab

1. Cardwire tool window > **Diff** tab. Decode as **ISO 8583**, Dialect **ISO 8583:1987 ASCII**.
2. Paste `diff-expected.hex` into the left box and `diff-actual.hex` into the right box. Click **Compare**.
3. Expand **DE 55** in the result so the changed 9F02 amount (15.00 to 25.00 USD) and the TVR
   change are visible, with DE 4, DE 11 changed and DE 23 removed.
4. In frame: both inputs, the summary line (1 added, 1 removed, 6 changed) and the colored tree.

## 5. Builder

1. Cardwire tool window > **Build** tab. Dialect **ISO 8583:1987 ASCII**, MTI `0100`.
2. Tick and fill: DE 2 `4111111111111111`, DE 3 `000000`, DE 4 `000000001500`, DE 11 `000123`,
   DE 41 `TERM0001`, DE 49 `840`.
3. Click **Edit EMV Tags…** and add 9F02 amount `15.00`, 5F2A currency `840`, 9A date `2026-09-26`,
   then OK.
4. Click **Build**, then set Output to **Java byte[]** (or jPOS) so the code export shows.
5. In frame: the field table, the Output selector and the generated output.

## 6. Error banner on a broken dialect (optional)

1. Open `.cardwire/dialects/broken-host.json`.
2. The red banner at the top reads "Cardwire cannot load this dialect: extends: unknown dialect
   'iso8583-1987-ascci'". If it does not show, save the file once (Ctrl+S) to refresh it.
3. In frame: the banner and the misspelled `extends` line.

## 7. Decode a selection from a log (optional)

1. Open `payment-switch.log`. On the third line, select the hex after `raw=` (double-click selects it).
2. Right-click > **Cardwire > Decode as ISO 8583**. The Decode tab opens with the message.
3. In frame: the log with the selection and the context menu open, or the result next to the log.

## Upload

- PNG, one per shot, named `01-decode.png`, `02-field55.png` and so on.
- Upload in the order above; the first one is the cover image on the plugin page.
- Before capturing, check that nothing personal is in frame (project names, other windows,
  notifications, your username in the title bar).
