# Inventory Tracker UI Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve reliability and user clarity for the included Inventory Tracker screens without changing the omitted screens.

**Architecture:** Make targeted edits in the existing Power Apps YAML files. Keep state local to the affected screens and use existing variable and collection patterns.

**Tech Stack:** Power Apps Canvas YAML, Power Fx formulas.

---

### Task 1: Rapid Scan Quantity and Submit Safety

**Files:**
- Modify: `Inventory_Tracker/RapidScan.pa.yaml`

- [ ] Change `NumberInput1.Default` from `ThisItem.OriginalQuantity` to `ThisItem.ScannedQuantity`.
- [ ] Add disabled states to `ClearSessionButton` and `SubmitAllButton` when `CountRows(colScannedItems) = 0`, `varIsSubmitting`, or `varIsProcessing`.
- [ ] Rewrite `SubmitAllButton.OnSelect` to collect failures in `colSubmitErrors`, clear the scan session only when no failures occur, and reset `varIsSubmitting` in both success and failure paths.
- [ ] Verify with `rg -n "Default: =ThisItem.ScannedQuantity|colSubmitErrors|CountRows\\(colScannedItems\\)" Inventory_Tracker\\RapidScan.pa.yaml`.

### Task 2: Archive Confirmation and Error Reporting

**Files:**
- Modify: `Inventory_Tracker/Inventory.pa.yaml`
- Modify: `Inventory_Tracker/ItemList.pa.yaml`

- [ ] Change each `Archive Expired Items` button to set a confirmation variable instead of archiving immediately.
- [ ] Add a confirmation container with Cancel and Archive actions on each affected screen.
- [ ] In each Archive action, collect candidate rows, patch archive records with `IfError`, remove inventory rows only when there are no archive errors, and show a count-based notification.
- [ ] Verify with `rg -n "varShowArchiveConfirm|colArchiveErrors|colArchiveCandidates|Archive selected items" Inventory_Tracker\\Inventory.pa.yaml Inventory_Tracker\\ItemList.pa.yaml`.

### Task 3: Settings and Scanner Copy

**Files:**
- Modify: `Inventory_Tracker/Settings.pa.yaml`
- Modify: `Inventory_Tracker/QuickPick.pa.yaml`
- Modify: `Inventory_Tracker/RapidScan.pa.yaml`

- [ ] Change the Settings save notification so it says the settings were applied for the current session.
- [ ] Use `varDefaultQuantity` in Quick Pick's quantity default.
- [ ] Replace scanner placeholders that blame Microsoft with direct task-focused scan instructions.
- [ ] Verify with `rg -n "current session|varDefaultQuantity|Click here, then scan|Scan the next barcode" Inventory_Tracker`.

### Task 4: Contextual Help

**Files:**
- Modify: `Inventory_Tracker/Inventory.pa.yaml`
- Modify: `Inventory_Tracker/Registration.pa.yaml`
- Modify: `Inventory_Tracker/QuickPick.pa.yaml`
- Modify: `Inventory_Tracker/RapidScan.pa.yaml`
- Modify: `Inventory_Tracker/ItemList.pa.yaml`
- Modify: `Inventory_Tracker/InventoryTool.pa.yaml`
- Modify: `Inventory_Tracker/Settings.pa.yaml`

- [ ] Add a Help button to each included screen.
- [ ] Add a screen-specific help overlay opened by the Help button and closed by a Close button.
- [ ] Verify with `rg -n "HelpButton_|HelpOverlay_|ctxShowHelp" Inventory_Tracker`.

### Task 5: Source Verification

**Files:**
- Read: modified YAML files

- [ ] Run targeted searches from Tasks 1-3.
- [ ] Run the contextual help search from Task 4.
- [ ] Run `git diff -- Inventory_Tracker docs` and review the changed formulas for accidental unrelated edits.
- [ ] Note that no live Power Apps runtime verification is available in this workspace.
