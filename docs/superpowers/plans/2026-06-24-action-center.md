# Action Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an HTML-based `Action Center` landing screen for inventory work that needs attention.

**Architecture:** Create one new Power Apps screen YAML file and update existing shared navigation formulas. The screen reads existing data sources only and uses `HtmlViewer` controls for dashboard summaries plus standard buttons for navigation.

**Tech Stack:** Power Apps Canvas YAML, Power Fx formulas, HtmlViewer controls.

---

### Task 1: Create Action Center Screen

**Files:**
- Create: `apps/inventory-tracker-powerapps/ActionCenter.pa.yaml`

- [ ] Add a new `ActionCenter` screen with the existing app visual pattern: shared `NavBar`, gray page background, white dashboard panels, quick action buttons, and contextual help.
- [ ] Add `HtmlViewer` content for summary cards, priority queue, restock, stale review, and recent updates.
- [ ] Keep row-level HTML read-only and use normal Power Apps buttons for navigation.
- [ ] Verify with `rg -n "Screens:|ActionCenter|SummaryHtml_ActionCenter|PriorityHtml_ActionCenter|HelpOverlay_ActionCenter" apps/inventory-tracker-powerapps\\ActionCenter.pa.yaml`.

### Task 2: Add Action Center to Navigation

**Files:**
- Modify: `apps/inventory-tracker-powerapps/Inventory.pa.yaml`
- Modify: `apps/inventory-tracker-powerapps/Registration.pa.yaml`
- Modify: `apps/inventory-tracker-powerapps/QuickPick.pa.yaml`
- Modify: `apps/inventory-tracker-powerapps/RapidScan.pa.yaml`
- Modify: `apps/inventory-tracker-powerapps/ItemList.pa.yaml`
- Modify: `apps/inventory-tracker-powerapps/InventoryTool.pa.yaml`
- Modify: `apps/inventory-tracker-powerapps/Settings.pa.yaml`
- Modify: `apps/inventory-tracker-powerapps/ActionCenter.pa.yaml`

- [ ] Add `{Label: "Action Center", Screen: ActionCenter}` as the first item in every nav table.
- [ ] Verify with `rg -n 'Label: "Action Center"' apps/inventory-tracker-powerapps`.

### Task 3: Landing-Screen Source Check

**Files:**
- Read: `apps/inventory-tracker-powerapps/*.pa.yaml`

- [ ] Search for app-level start-screen source with `rg -n "StartScreen|OnStart|App.ActiveScreen" apps/inventory-tracker-powerapps`.
- [ ] If a writable app-level start-screen property exists, set it to `ActionCenter`.
- [ ] If no app-level start-screen source exists in the unpacked files, document that Power Apps Studio may need to set `ActionCenter` as the start screen after import.

### Task 4: Verification

**Files:**
- Read: `apps/inventory-tracker-powerapps/*.pa.yaml`

- [ ] Load all `.pa.yaml` files with `yaml.BaseLoader` to verify structural YAML.
- [ ] Run `git diff --check -- apps/inventory-tracker-powerapps docs`.
- [ ] Run targeted searches from Tasks 1-3.
- [ ] Review `git diff --stat -- apps/inventory-tracker-powerapps docs`.
