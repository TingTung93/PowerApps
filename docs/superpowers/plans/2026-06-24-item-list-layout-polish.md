# Item List Layout Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve the `ItemList` screen's layout, scanability, and visual polish without changing its core behavior.

**Architecture:** Edit only `Inventory_Tracker/ItemList.pa.yaml` for UI changes. Preserve the existing data source, gallery selection behavior, form behavior, archive confirmation behavior, and help overlay.

**Tech Stack:** Power Apps Canvas YAML, Power Fx formulas, HtmlViewer row markup.

---

### Task 1: Header and Filter Toolbar

**Files:**
- Modify: `Inventory_Tracker/ItemList.pa.yaml`

- [ ] Add a compact summary header below the nav with total, expired, low-stock, and expiring-soon counts.
- [ ] Restyle `SearchContainer_ItemList` as a full-width toolbar with white fill, better padding, and responsive input widths.
- [ ] Verify with `rg -n "SummaryHeader_ItemList|SummaryHtml_ItemList|SearchContainer_ItemList" Inventory_Tracker\\ItemList.pa.yaml`.

### Task 2: Split Layout and Gallery Rows

**Files:**
- Modify: `Inventory_Tracker/ItemList.pa.yaml`

- [ ] Change the main split view to use full available width instead of `Parent.Width-300`.
- [ ] Set the gallery width to a responsive 42 percent on larger screens and full width on small screens.
- [ ] Increase gallery row height and update row HTML to a cleaner inventory-row layout with quantity and expiration status badges.
- [ ] Verify with `rg -n "Width: =Parent.Width|TemplateSize: =84|HtmlText4" Inventory_Tracker\\ItemList.pa.yaml`.

### Task 3: Detail Pane Header

**Files:**
- Modify: `Inventory_Tracker/ItemList.pa.yaml`

- [ ] Improve action bar spacing and add a selected-item header text in the right pane.
- [ ] Preserve existing New, Edit, Delete, Archive, Back behavior.
- [ ] Verify with `rg -n "SelectedItemHeader_ItemList|ArchiveExpiredButton_1|NewRecordButton1_4|EditButton1_4|DeleteButton1_4" Inventory_Tracker\\ItemList.pa.yaml`.

### Task 4: Verification

**Files:**
- Read: `Inventory_Tracker/*.pa.yaml`

- [ ] Load all `.pa.yaml` files with `yaml.BaseLoader`.
- [ ] Run `git diff --check -- Inventory_Tracker docs`.
- [ ] Run all targeted searches from Tasks 1-3.
- [ ] Review `git diff -- Inventory_Tracker\\ItemList.pa.yaml`.
