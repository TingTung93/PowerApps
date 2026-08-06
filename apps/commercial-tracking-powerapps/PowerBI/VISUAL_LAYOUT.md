# Power BI Visual Layout

## Page 1 — Daily Operations

Use a standard **16:9** report page. Keep the Filters pane hidden for consumers and expose only the slicers listed below.

### Header

- Title: `Commercial Package Tracking`
- Subtitle measure: `Last Refresh Label`
- Background: white
- Bottom border: `#D2D0CE`

### Slicers

Place one horizontal row beneath the header:

1. `Received Date` — Between or dropdown; default to the latest available date.
2. `Location` — Dropdown.
3. `Status` — Dropdown.
4. `Carrier` — Dropdown.
5. `ManifestID` — Dropdown with search.

Enable **Select all** for Location, Status, and Carrier. Keep single-select off.

### KPI Cards

Use five cards in one row:

| Card | Measure | Semantic color |
|---|---|---|
| Received | `Inbound Packages` | Green |
| Active | `Active Packages` | Blue |
| Needs Recipient | `Unassigned Packages` | Amber |
| Picked Up | `Picked Up Packages` | Gray |
| Voided | `Voided Packages` | Red |

Use 24–28 pt callout values and 10–11 pt labels. Do not use gradients or decorative icons.

### Location Workload

Use a clustered bar chart:

- Y-axis: `Location`
- X-axis: `Package Count`
- Small multiples or legend: `Status`, only if the result remains readable.
- Sort descending by `Package Count`.
- Turn data labels on.

### Carrier Workload

Use a compact column chart:

- X-axis: `Carrier`
- Y-axis: `Package Count`
- Show the top 8 carriers by package count.
- Group blank carrier values as `Unknown` in the visual.

### Active Package Table

Use a native Table visual with:

1. `Tracking Number`
2. `Carrier`
3. `Status`
4. `Recipient`
5. `Location`
6. `Received At Local`
7. `Received By Name`
8. `ManifestID`

Apply a visual filter to active statuses when this table is intended as a work queue. Use 10 pt values, 10 pt semibold headers, alternating rows off, and a subtle horizontal grid.

Conditional formatting:

- `Status = Picked Up` — green font.
- `Status = Voided` — red font.
- `Assignment State = Needs Recipient` — amber background on Recipient.

## Page 2 — Manifest Finder

This page finds, previews, and launches printable manifests. The paginated report—not the preview table—is the authoritative print surface.

### Search Controls

- `Received Date`
- Optional: `Location`
- Optional: `ManifestID`
- Optional: `Recipient`

Expose these as compact dropdown slicers in a 0.45-inch control strip at the top. If the report is published with a fixed daily bookmark, hide the slicers after setting the bookmark.

### Header Band

- Left: card using `Daily Manifest Title`.
- Beneath it: card using `Daily Manifest Subtitle`.
- Right: `Package Count`, `First Receipt Time`, and `Last Receipt Time`.
- Footer/right edge: `Last Refresh Label`.

Keep the header under 0.8 inches tall.

### Readiness Banner

Use a card with `Print Row Warning`.

- Conditional font or background color based on `Print Row Warning Color`.
- Green means the table should fit the standard snapshot.
- Red means verify or narrow the selection before printing.

### Manifest Preview Table

Use one native Table visual filling the remaining page.

| Order | Field | Suggested width |
|---:|---|---:|
| 1 | `Package Reference` | 0.75 in |
| 2 | `Tracking Number` | 1.65 in |
| 3 | `Carrier` | 0.75 in |
| 4 | `Location` | 1.05 in |
| 5 | `Received At Local` | 1.25 in |
| 6 | `Received By Name` | 1.35 in |
| 7 | `Recipient` | 1.35 in |
| 8 | `ManifestID` | 1.20 in |
| 9 | `Status` | 0.85 in |

Formatting:

- Values: Segoe UI, 8 pt.
- Headers: Segoe UI Semibold, 8 pt.
- Row padding: 2 px.
- Word wrap: off for values and headers.
- Totals: off.
- Vertical grid: off.
- Horizontal grid: `#EDEBE9`, 1 px.
- Background: white.
- Sort: `Received At Local`, ascending.

Do not add Notes, Sender, Created By, or Modified By to this page.

### Primary Actions

- Add a large green **Print Manifest** button that opens `Commercial Tracking Manifest.rdl` in the Power BI service.
- Add a gray **Export Preview Data** instruction beside the table.
- Treat this table as a search and verification preview, not as the archival PDF.
- Confirm the report mode, Manifest ID, date, location, and package count in the paginated report before printing.

## Paginated Report Companion

Use this when every matching row must print, even when the report spans multiple pages.

Page settings:

- Paper: Letter
- Orientation: Landscape
- Margins: 0.25 inches
- Body width: no more than 10.5 inches
- Header: 0.55 inches
- Footer: 0.30 inches

Create parameters for:

- `ReportDate`
- `Location`
- `ManifestID`

Use a tablix with the same columns and widths as the Daily Manifest table. Set the header row to repeat on each page and keep the header visible while scrolling. Add:

- Page header: report title, selected filters, run time.
- Page footer: `Page X of Y`, organizational handling statement, and report run time.
- Grouping option: `ManifestID`, with a page break between manifests when separate custody records are required.

This version is the archival-quality choice for long daily manifests and Excel/PDF export.
