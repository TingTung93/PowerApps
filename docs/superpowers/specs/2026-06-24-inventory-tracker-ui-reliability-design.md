# Inventory Tracker UI Reliability Pass

## Scope

This pass applies only to the checked-in screens under `Inventory_Tracker`. `Table View` and `Archive` are intentionally out of scope.

## Approach

Use targeted Power Apps YAML edits that preserve the existing structure and control patterns. Avoid new app-wide architecture, new data sources, or a visual redesign.

## Changes

- Rapid Scan: make the editable quantity represent `ScannedQuantity`, not previous inventory quantity. Disable destructive or submit actions when no scans are present or a submission is running.
- Bulk submit: collect per-row submit failures, avoid clearing the session when failures occur, and show a more accurate success or error message.
- Archive actions: replace immediate archive execution with an explicit confirmation state and add basic error collection before removing inventory rows.
- Settings: make the current behavior honest by changing the save action copy to indicate settings are applied for the current session, and use the configured default quantity in Quick Pick.
- Copy and polish: remove unprofessional scanner placeholder text and improve scan instructions.
- Contextual help: add a small Help action and screen-specific overlay to each included screen so users can understand the current workflow without leaving the screen.

## Error Handling

Bulk operations collect failures into local collections and report counts. Successful bulk operations clear the session only after no failures are recorded.

## Contextual Help

Each included screen gets a lightweight overlay opened from a Help button. Help text is concise and workflow-specific: what the screen is for, the normal path through the screen, and the highest-risk action on that screen.

## Verification

Verify the edited YAML with targeted searches and review the changed formulas for balanced state transitions. There is no automated Power Apps runtime available in this workspace.
