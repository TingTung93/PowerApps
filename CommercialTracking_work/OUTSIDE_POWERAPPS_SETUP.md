# Commercial Tracking: Setup Outside Power Apps

This checklist covers the work that cannot be completed by pasting formulas or `.pa.yaml` into Power Apps Studio.

## 1. Confirm the SharePoint `Tracking` list

The app expects the existing SharePoint list named `Tracking` and these columns:

| Display name | Internal name | Type |
|---|---|---|
| Tracking Number | `Title` | Single line of text |
| Carrier | `field_1` | Single line of text |
| Direction | `field_2` | Single line of text |
| Status | `field_3` | Single line of text |
| Recipient | `field_4` | Single line of text |
| Sender | `field_5` | Single line of text |
| Logged Date/Time | `field_6` | Date and time |
| Notes | `field_7` | Single line of text |
| Signed Out Date/Time | `SignedOutDateTime` | Date and time |
| Signed Out By | `SignedOutBy` | Person or group |

Do not rename or recreate an existing SharePoint column to change its internal name. If a required column is missing, create it and then update the corresponding Power Fx field reference to the internal name SharePoint assigns.

Recommended SharePoint configuration:

- Index `Title`, `field_4` (Recipient), `field_3` (Status), and `field_6` (Logged Date/Time).
- Enable list version history for audit and recovery.
- Give receiving staff Contribute access and supervisors Edit access.
- Restrict the list and any future PDF archive library to the intended commercial-tracking team.
- Review retention requirements before storing recipient or label data.

## 2. Packing-list barcodes

Inbound receiving manifests and recipient custody manifests generate Code 39 barcodes as inline SVG directly in Power Fx. They make no external network request and require no installed barcode font, JavaScript package, browser extension, or local service. To keep each symbol compact and reliably scannable, it encodes `PKGID-<SharePoint record ID>`; the scanner resolves that reference to the existing package record while the full tracking number remains visible in the same row.

Validate several short and long carrier tracking numbers with the exact scanners and print settings used in production. Keep printing at 100% scale where possible; aggressive browser scaling can make narrow bars harder to read.

## 3. Refresh connections after source changes

After adding the screen and formulas in Power Apps Studio:

1. Open **Data** and refresh the `Tracking` SharePoint connection.
2. Confirm Power Apps recognizes `Recipient`, `Status`, `SignedOutDateTime`, and `SignedOutBy`.
3. Resolve any connection reference prompts with the production SharePoint connection.
4. Save, publish, and verify that the published version—not only Studio preview—can create and update list items.

## 4. Configure scanner hardware

- Configure keyboard-wedge scanners to send an Enter/Return suffix so the text input's `OnChange` fires after each scan.
- Test normal carrier barcodes, supported 2D labels, and printed packing-list Code 39 barcodes.
- Confirm the scanner does not add unwanted prefixes, suffixes, or control characters.
- Confirm the fixed receiving-location choices in `LedgerScreen.pa.yaml` match the production locations. The supplied list is `Main Receiving`, `Loading Dock`, `Mailroom`, and `Warehouse`.
- Test both **Inbound** receiving and **Outbound** pickup modes.

## 5. Deferred PDF archive flow

The canvas app currently uses `Print()` for a customer copy or browser PDF. Automated archive generation is intentionally deferred.

When ready, create a Power Automate flow using the contract in [`flow-contract.json`](./flow-contract.json). The flow should:

1. Receive the recipient, manifest ID, issuing user, and package IDs from Power Apps.
2. Retrieve the matching SharePoint records server-side.
3. Populate an approved Word or HTML template.
4. Convert it to PDF.
5. Save the immutable PDF to a restricted SharePoint document library.
6. Return the archive URL, filename, and generated timestamp to Power Apps.

Create the archive library with versioning, retention, and least-privilege permissions before connecting the flow.

## 6. Production acceptance checks

- Select each allowed receiving location and confirm inbound scanning works without entering a recipient.
- Confirm every inbound and outbound scan appends a Notes line containing the timestamp, signed-in user, and location.
- Scan an already-active inbound package and confirm no duplicate record is created and the repeat scan is appended to Notes.
- Print the current session's inbound receiving manifest and confirm it contains only successfully created inbound records from that local scan session.
- Assign high-value test packages to a recipient, create the recipient custody manifest, and confirm another recipient's packages are excluded.
- Print a manifest containing at least 16 items on one page and scan several of the compact inline Code 39 package-reference barcodes.
- Print manifests containing 21, 50, and 100 items. Confirm they automatically use the four-column high-density audit index and remain on one page at 100% print scale. High-density manifests intentionally omit per-item barcodes to preserve readable tracking and audit data.
- In Outbound mode, confirm a QR scan marks only the intended record as `Picked Up` and sets `SignedOutDateTime`.
- Confirm duplicate inbound scans do not create duplicate active records.
- Confirm voiding a Ledger entry changes only the record identified by its SharePoint ID.
- Confirm returning from History does not clear the local Ledger.
- Test with a non-owner receiving account to verify real production permissions.

