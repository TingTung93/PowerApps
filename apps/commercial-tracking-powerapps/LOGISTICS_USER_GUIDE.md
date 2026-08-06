# Commercial Package Tracking

## Logistics Personnel User Guide

This application records inbound packages, package locations, recipient assignments, outbound custody, and printable manifests.

## Before You Begin

1. Sign in to the workstation with your assigned account.
2. Open the Commercial Package Tracking Power App.
3. Confirm the barcode scanner is connected.
4. Confirm the scanner sends an Enter or Return after each scan.
5. Select the receiving location where packages will be held.

Do not use another person’s account. The application records the signed-in user with receiving, pickup, and manifest activity.

## Receive Inbound Packages

Inbound receiving is recipient-agnostic. You do not need to identify or enter a recipient before scanning.

1. Set the mode to **Inbound**.
2. Select the correct receiving location:
   - Main Receiving
   - Loading Dock
   - Mailroom
   - Warehouse
3. Place the cursor in the scanner field if it is not already active.
4. Scan the carrier barcode.
5. The scanner locks while SharePoint saves the record.
6. Wait for the persistent result panel to show **SUCCESS**, **WARNING**, or **ERROR** before scanning the next package.

For each successful inbound scan, the application records:

- Tracking number
- Carrier
- Receiving location
- Date and time received
- Receiving user
- Status
- SharePoint Modified and Modified By metadata

### Successful Scan

A successful scan displays a message such as:

> Package received at Main Receiving.

The package will appear in the Recent Scans ledger.

The scanner also uses short offline sound cues:

- A high tone accompanies **SUCCESS**.
- A middle tone accompanies **WARNING**.
- A low tone accompanies **ERROR**.

Always verify the written result and tracking number. Sound is supplemental and may be muted by workstation or browser settings.

### Status Feedback

The scanner result panel uses green for success, amber for a warning, and red for an error. Always read the written status and tracking number; color and sound are supplemental.

### Repeat Scan

If the package is already active, the application will not create a second active record. It updates the location to the currently selected receiving location while preserving the original Received At and Received By values.

Confirm that the selected location is correct before intentionally rescanning a package.

## Review the Recent Scans Ledger

The ledger shows activity from the current local session.

You can search by:

- Tracking number
- Carrier
- Recipient
- Location
- Label details
- Notes

The ledger is not the complete database history. Use **View Database History** to search records from previous sessions.

### Void an Incorrect Scan

Use the trash icon only when a scan was entered incorrectly.

1. Select the trash icon once.
2. Confirm the icon changes to a warning symbol.
3. Select the warning icon again within eight seconds to complete the void.

Voiding:

- Changes the SharePoint record status to **Voided**
- Removes the entry from the local ledger
- Does not permanently delete the database record

If uncertain, stop and ask a supervisor before voiding a record.

## Print an Inbound Receiving Manifest

The inbound manifest is the record of packages received during the current local scan session.

1. Complete the inbound scanning batch.
2. Review the Recent Scans ledger.
3. Confirm the unprinted count is correct.
4. Select **Print Inbound Manifest**.
5. Review the location, item count, and Manifest ID.
6. Select **Print / Save PDF**.
7. Choose the approved printer and print at 100% scale when possible.
8. Sign the receiving record if required by local procedure.

Printing records the same Manifest ID on every included package and records the signed-in user as the person who printed the manifest.

The application writes the manifest audit information before opening the browser print dialog. If you cancel the print dialog, notify a supervisor or print the manifest again from the same screen.

### Manifest Size

- 1–20 items use the detailed layout.
- 21–100 items use a four-column high-density layout.
- High-density manifests omit individual barcodes so that up to 100 records can fit on one page.

If more than 100 items are present, divide receiving into smaller sessions or follow local overflow procedures.

## Clear the Local Ledger

Do not clear the ledger before printing the inbound manifest.

If unprinted inbound records exist:

1. The first selection of **Clear Local Ledger** displays a warning.
2. To continue, select the confirmation button again within eight seconds.

Clearing the ledger removes the current workstation’s session view. It does not delete saved SharePoint records, but it removes the session grouping used to build the inbound manifest.

## Assign or Reconcile a Recipient

Packages may be received before the recipient is known.

1. Select **Reconcile Unassigned Packages**.
2. Locate the package.
3. Select the edit icon.
4. Enter or assign the correct recipient.
5. Save the change.

Use **Edit All Fields** only when a complete record correction is necessary. This opens the supervisor package editor, which includes SharePoint fields such as Location, receiving information, and manifest information.

Do not change receiving or manifest audit fields unless correcting a verified error.

## Search Database History

Select **View Database History** to search the complete Tracking list.

Search accepts:

- Tracking number
- Manifest ID
- Carrier
- Recipient
- Location
- Status
- Notes

Use at least three characters for a full search. Without a search, the screen shows recent records.

History cards display receiving information, location, status, recipient, and Manifest ID when available.

## Release Packages Outbound

Outbound mode records package pickup or custody transfer.

1. Confirm the recipient assignment is correct.
2. Set the mode to **Outbound**.
3. Scan the carrier barcode or supported package-reference barcode.
4. Confirm the application displays the correct recipient and success message.

A successful outbound scan:

- Changes Direction to **Outbound**
- Changes Status to **Picked Up**
- Records the pickup date and time
- Records the signed-in user
- Updates SharePoint Modified and Modified By metadata

Never release a package when the displayed recipient or record does not match the person accepting custody.

## Print a Recipient Custody Manifest

Use this manifest for high-value packages or other items requiring documented transfer to a recipient.

1. Enter the recipient in **Outbound manifest recipient**.
2. Select **Print Recipient Custody List**.
3. Verify the recipient, item count, and Manifest ID.
4. Select **Print / Save PDF**.
5. Print at 100% scale when possible.
6. Have the recipient sign and date the custody acceptance section.
7. Retain the signed copy according to local records procedures.

Only packages assigned to the selected recipient should appear.

## Common Messages

### Select a receiving location before scanning

Inbound scanning is disabled until a location is selected.

### No supported tracking number was found

- Rescan the carrier barcode.
- Ensure the scanner is reading the tracking barcode rather than another label element.
- Enter the tracking number manually only when permitted by local procedure.

### Package was already active

The application retained the existing record and updated its location. Verify the package and location.

### Multiple active records exist

Stop processing that package and notify a supervisor. The duplicate records must be reconciled before pickup or additional receiving.

### No package awaiting pickup was found

Verify the tracking number and search Database History. Do not create an outbound record manually.

### Package or manifest could not be saved

Do not assume the transaction was recorded.

1. Keep the package in the controlled receiving area.
2. Record the tracking number separately if required.
3. Check network connectivity.
4. Retry once.
5. Notify a supervisor if the error continues.

### Barcode does not scan from a printed manifest

- Confirm the document was printed at 100% scale.
- Try the original carrier barcode.
- Search by the printed tracking number.
- Do not repeatedly scan an unreadable symbol.

## End-of-Shift Checklist

Before leaving the receiving station:

1. Confirm all packages physically present were scanned.
2. Confirm each package shows the correct Location.
3. Reconcile unassigned packages when recipient information is available.
4. Print all required inbound manifests.
5. Confirm the unprinted inbound count is zero.
6. Complete required recipient custody manifests.
7. Resolve or report duplicate and failed scans.
8. File signed manifests according to local records procedures.
9. Clear the local ledger only after required manifests are complete.

## Information Protection

- Use only authorized workstations and accounts.
- Do not photograph package records or manifests with personal devices.
- Do not send tracking, recipient, or manifest information through unapproved systems.
- Secure printed manifests according to local policy.
- Report suspected unauthorized access or disclosure through the appropriate local process.

## Supervisor Escalation

Contact a supervisor when:

- Multiple active records exist for one tracking number
- A package cannot be saved after retrying
- The recipient identity does not match the record
- A high-value package has incomplete custody information
- A manifest was marked printed but no physical copy was produced
- A receiving or manifest audit field requires correction
- A package appears missing, damaged, or delivered to the wrong location
