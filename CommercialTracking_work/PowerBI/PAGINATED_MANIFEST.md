# Paginated Manifest Report

Build this report in Power BI Report Builder after publishing the Power BI semantic model. Save it as `Commercial Tracking Manifest.rdl`.

## Why This Is the Print Surface

The report must print every selected package rather than only the rows visible in a Power BI table visual. A paginated report provides physical page sizing, repeated headers, controlled page breaks, page numbers, PDF export, and Excel export.

## Data Source

1. Publish the Commercial Tracking Power BI semantic model to the approved workspace.
2. In Report Builder, create a data source using **Power BI semantic model connection**.
3. Select the published Commercial Tracking semantic model.
4. Create a dataset named `ManifestRows`.
5. Paste the query from `PaginatedDataset.dax`.

## Parameters

Create these report parameters:

| Name | Type | Default | Notes |
|---|---|---|---|
| `ReportMode` | Text | `Exact Manifest` | Available values: Exact Manifest, Daily Receiving, Recipient Custody |
| `ManifestID` | Text | blank | Required for Exact Manifest |
| `ReportDate` | Date/Time | Today | Used by Daily Receiving |
| `Location` | Text | blank | Blank means all approved locations |
| `Recipient` | Text | blank | Required for Recipient Custody |

Location available values:

- blank / All Locations
- Main Receiving
- Loading Dock
- Mailroom
- Warehouse

Add visible parameter instructions at the top of the report:

- **Exact Manifest:** enter the full Manifest ID.
- **Daily Receiving:** choose date and optionally location.
- **Recipient Custody:** enter the recipient and optionally location.

## Validation Messages

Add a red text box above the tablix with this expression:

```text
=Switch(
  Parameters!ReportMode.Value = "Exact Manifest" And Trim(Parameters!ManifestID.Value) = "",
    "STOP: Manifest ID is required.",
  Parameters!ReportMode.Value = "Recipient Custody" And Trim(Parameters!Recipient.Value) = "",
    "STOP: Recipient is required.",
  CountRows("ManifestRows") = 0,
    "STOP: No packages match the selected parameters.",
  CountRows("ManifestRows") > 100 And Parameters!ReportMode.Value <> "Daily Receiving",
    "ATTENTION: This report contains more than 100 packages. Verify the selection before printing.",
  True,
    Nothing
)
```

Hide the tablix when the parameter selection is invalid or returns no rows.

## Page Definition

- Paper: Letter
- Orientation: Landscape
- Page width: 11 in
- Page height: 8.5 in
- Left/right margins: 0.25 in
- Top/bottom margins: 0.25 in
- Maximum body width: 10.5 in
- Header height: 0.55 in
- Footer height: 0.30 in

Avoid any object extending beyond the 10.5-inch body width; even a small overrun can create a blank extra page.

## Header

Left side:

```text
COMMERCIAL PACKAGE TRACKING
```

Second line:

```text
=Switch(
  Parameters!ReportMode.Value = "Exact Manifest",
    "AUDITED MANIFEST | " & Parameters!ManifestID.Value,
  Parameters!ReportMode.Value = "Daily Receiving",
    "DAILY RECEIVING REPORTING EXTRACT | " & Format(Parameters!ReportDate.Value, "MM/dd/yyyy"),
  Parameters!ReportMode.Value = "Recipient Custody",
    "RECIPIENT CUSTODY REPORTING EXTRACT | " & Parameters!Recipient.Value
)
```

Right side:

```text
="Packages: " & CountRows("ManifestRows")
```

Third line:

```text
="Location: " &
  IIF(Trim(Parameters!Location.Value) = "", "All Locations", Parameters!Location.Value) &
  " | Generated: " & Format(Globals!ExecutionTime, "MM/dd/yyyy hh:mm tt")
```

Use **Audited Manifest** only when filtering by an existing nonblank Manifest ID. All ad hoc selections must say **Reporting Extract**.

## Tablix

Use these columns:

| Field | Header | Width |
|---|---|---:|
| `PackageReference` | Package Ref | 0.75 in |
| `TrackingNumber` | Tracking Number | 1.65 in |
| `Carrier` | Carrier | 0.70 in |
| `Location` | Location | 0.95 in |
| `ReceivedAtLocal` | Received | 1.15 in |
| `ReceivedByName` | Received By | 1.20 in |
| `Recipient` | Recipient | 1.30 in |
| `ManifestID` | Manifest ID | 1.15 in |
| `Status` | Status | 0.85 in |

Formatting:

- Header: Segoe UI Semibold, 7.5 pt, white text, `#0566B2` background.
- Detail: Segoe UI, 7.5 pt.
- Row height: 0.20–0.22 in.
- Borders: bottom border `#D2D0CE`, 0.5 pt.
- Padding: 2 pt.
- CanGrow: false for all detail text boxes.
- Truncate long values rather than increasing row height.
- Repeat header row on every page.
- Keep header visible while scrolling.

Use an alternating row background of white and `#F8F8F8`. Do not use color alone to communicate status.

## Footer

Left:

```text
FOR OFFICIAL USE — STORE AND DISTRIBUTE ONLY IN APPROVED ORGANIZATIONAL LOCATIONS
```

Right:

```text
="Page " & Globals!PageNumber & " of " & Globals!TotalPages
```

## Signature Block

For Exact Manifest mode, add this block after the tablix:

```text
Released/Received By: ______________________________

Signature: _________________________________________

Date/Time: _________________________________________
```

Set the block to remain together when possible. For Daily Receiving and Recipient Custody reporting extracts, hide it unless local policy requires certification.

## Print and Export Procedure

1. Open **Manifest Finder** in the Power BI report.
2. Locate and copy the exact Manifest ID, or choose the daily/recipient reporting parameters.
3. Select **Print Manifest** to open the paginated report.
4. Confirm the title says either **Audited Manifest** or **Reporting Extract**, as intended.
5. Confirm package count, location, and first/last tracking numbers.
6. Export to PDF for printing or Excel for reconciliation.
7. Print at 100% / Actual Size.
8. Store the output in the approved record-keeping location.

## Power BI Report Button

On the Manifest Finder page, add a large green button:

- Text: `Print Manifest`
- Accessible label: `Open the printable paginated package manifest`
- Action: Web URL
- URL: the published paginated report URL

Add a nearby gray secondary button:

- Text: `Export Preview Data`
- Instruction: use the native table visual menu and select **Export data**.

If your tenant supports URL parameters for the published paginated report, pass the Manifest ID or other selected parameter values in the link. Otherwise, users select parameters after the report opens.
