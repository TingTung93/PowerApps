# Action Center Design

## Goal

Add an `Action Center` screen and make it the app landing screen. The screen gives users a single operational view of the inventory items that need attention today.

## Scope

This first version uses existing data sources only:

- `Inventories`
- `GTIN_Lookups` where PAR or product metadata is needed
- Existing screens for drill-in actions

No new Dataverse tables, flows, or screen-specific persistence are required.

## Screen Role

`Action Center` is a read-only dashboard. It should answer:

- What is expired?
- What will expire soon?
- What is out of stock or low stock?
- What has not been checked recently?
- Which existing workflow should the user open next?

The screen becomes the default landing screen and is added as the first item in the shared navigation table.

## Layout

Use the existing app structure: a full-height vertical container, shared `NavBar`, and a light gray page background with white content panels.

The main content should include:

- Summary strip with compact cards for expired, expiring soon, zero stock, low stock, and stale checks.
- Main priority queue rendered with `HtmlViewer`, sorted by severity.
- Secondary HTML panels for needs restock, needs review, and recently updated.
- Quick action buttons to `QuickPick`, `RapidScan`, `Inventory`, `Registration`, and `InventoryTool`.
- Contextual Help overlay matching the help pattern already added to the other screens.

## HTML Usage

Use `HtmlViewer` for status cards and read-only list previews. Do not rely on HTML for row-level interactivity. Interactive actions should remain normal Power Apps buttons outside the HTML.

## Data Rules

- Expired: `Expiration < Today()`
- Expiring soon: `Expiration >= Today() && Expiration <= DateAdd(Today(), Coalesce(varDaysBeforeExpiryWarning, 30), TimeUnit.Days)`
- Zero stock: `Quantity <= 0`
- Low stock: `Quantity > 0 && Quantity < Coalesce(PAR, 5)` when PAR data is available; otherwise use a conservative fallback threshold of 5.
- Stale checks: `'Modified On' < DateAdd(Today(), -30, TimeUnit.Days)`

## Empty States

Each panel should show useful empty-state text, such as "No expired items" or "No low-stock items found", instead of rendering a blank area.

## Error Handling

This screen does not modify data. Risk is mainly delegation/performance. Use `FirstN` on preview lists and count-based summaries to keep the screen responsive.

## Verification

Verify by loading the updated YAML structurally, checking that `ActionCenter.pa.yaml` exists, confirming the nav includes `Action Center`, and confirming the landing/default app reference is updated where available in the unpacked source.
