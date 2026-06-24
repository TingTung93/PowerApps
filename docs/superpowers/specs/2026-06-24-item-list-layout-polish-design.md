# Item List Layout Polish Design

## Goal

Improve the `ItemList` screen's formatting and layout while preserving its existing search, filter, selection, edit, archive, and help behavior.

## Scope

Only `Inventory_Tracker/ItemList.pa.yaml` is in scope for UI changes. Existing local formula fixes in related files remain untouched.

## Design

The screen should feel like a focused split-view inventory work surface:

- Add a compact summary header below navigation with item count, expired count, low-stock count, and expiring-soon count.
- Restyle the search/category controls into a full-width toolbar with better alignment and responsive widths.
- Make the main split view use the full available width instead of the current constrained `Parent.Width-300`.
- Give the gallery more breathing room with taller rows and cleaner row HTML.
- Keep status color meaningful but less visually noisy.
- Improve the detail pane header/action bar with stronger spacing and clearer action grouping.
- Preserve archive confirmation and contextual help overlays.

## Data Rules

- Expired: `Expiration < Today()`
- Expiring soon: `Expiration >= Today() && Expiration <= DateAdd(Today(), Coalesce(varDaysBeforeExpiryWarning, 30), TimeUnit.Days)`
- Low stock: `Quantity < 5`

## Verification

Verify structural YAML parsing, targeted searches for the updated controls/styles, and `git diff --check`. Live Power Apps rendering is not available from this workspace.
