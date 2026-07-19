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

## 2. Allow the QR image endpoint

Packing lists load QR images from:

```text
https://api.qrserver.com/v1/create-qr-code/
```

Confirm that this domain is reachable from the browsers, network, and tenant used by receiving staff. The QR payload contains only `PKG|<tracking number>`; do not add recipient names or addresses to the URL.

If external QR services are prohibited, replace this endpoint with an approved internal service before production use.

## 3. Refresh connections after source changes

After adding the screen and formulas in Power Apps Studio:

1. Open **Data** and refresh the `Tracking` SharePoint connection.
2. Confirm Power Apps recognizes `Recipient`, `Status`, `SignedOutDateTime`, and `SignedOutBy`.
3. Resolve any connection reference prompts with the production SharePoint connection.
4. Save, publish, and verify that the published version—not only Studio preview—can create and update list items.

## 4. Configure scanner hardware

- Configure keyboard-wedge scanners to send an Enter/Return suffix so the text input's `OnChange` fires after each scan.
- Test normal carrier barcodes, supported 2D labels, and printed `PKG|...` QR codes.
- Confirm the scanner does not add unwanted prefixes, suffixes, or control characters.
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

- Scan two inbound packages for one recipient and confirm both appear on that recipient's packing list.
- Confirm another recipient cannot see those packages on their packing list.
- Print the packing list and scan each inline QR code.
- In Outbound mode, confirm a QR scan marks only the intended record as `Picked Up` and sets `SignedOutDateTime`.
- Confirm duplicate inbound scans do not create duplicate active records.
- Confirm voiding a Ledger entry changes only the record identified by its SharePoint ID.
- Confirm returning from History does not clear the local Ledger.
- Test with a non-owner receiving account to verify real production permissions.

