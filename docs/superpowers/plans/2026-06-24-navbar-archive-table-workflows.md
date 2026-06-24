# Navbar, Archive, and Table Workflows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add centralized navbar defaults, an Archive restore workflow, and a Table View selected-record preview.

**Architecture:** Keep the Power Apps YAML files as the source of truth. Add a lightweight Python source validator because the repository does not provide a Power Apps runtime test harness. Implement behavior through existing variables, modern controls, `IfError`, and table/search patterns already used elsewhere in the app.

**Tech Stack:** Power Apps YAML, Power Fx formulas, Python source validation, Git.

---

### Task 1: Source Validator

**Files:**
- Create: `scripts/validate_powerapps_source.py`

- [ ] **Step 1: Add validator checks**

Create a Python script that parses every `Inventory_Tracker/*.pa.yaml` file and asserts expected source patterns:

```python
from pathlib import Path
import sys
import yaml

ROOT = Path(__file__).resolve().parents[1]
APP_DIR = ROOT / "Inventory_Tracker"

def read(name):
    return (APP_DIR / name).read_text(encoding="utf-8")

def require(condition, message, failures):
    if not condition:
        failures.append(message)

def main():
    failures = []
    for path in sorted(APP_DIR.glob("*.pa.yaml")):
        with path.open("r", encoding="utf-8") as handle:
            yaml.load(handle, Loader=yaml.BaseLoader)

    navbar = read("NavBarComponent.pa.yaml")
    archive = read("Archive.pa.yaml")
    table_view = read("Table_View.pa.yaml")

    require('{Label: "Action Center", Screen: ActionCenter}' in navbar, "navbar default includes Action Center", failures)
    require("TemplateSize:" in navbar and "Parent.Width < 900" in navbar, "navbar has responsive template sizing", failures)
    require("RestoreButton_Archive" in archive, "archive has a restore button", failures)
    require("varArchivedItem" in archive, "archive tracks selected archived item", failures)
    require("IfError(" in archive and "Remove(Expireds, varArchivedItem)" in archive, "archive restore is guarded by IfError before remove", failures)
    require("PreviewHtml_TableView" in table_view, "table view has selected item preview", failures)
    require("OpenInventoryButton_TableView" in table_view, "table view uses explicit open action", failures)
    require("varTableViewItem" in table_view, "table view tracks selected item", failures)

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        return 1

    print("Power Apps source validation OK")
    return 0

if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: Run validator to verify it fails**

Run: `python scripts/validate_powerapps_source.py`

Expected: exits `1` and reports missing Archive restore and Table View preview patterns.

### Task 2: Navbar Defaults

**Files:**
- Modify: `Inventory_Tracker/NavBarComponent.pa.yaml`
- Modify: `Inventory_Tracker/Archive.pa.yaml`
- Modify: `Inventory_Tracker/Table_View.pa.yaml`

- [ ] **Step 1: Keep navbar default Items complete**

Confirm `NavBarComponent.pa.yaml` includes Action Center first and all existing screens. Keep responsive template sizing.

- [ ] **Step 2: Remove redundant Items tables from Archive and Table View**

Delete the `Items: |+` blocks from `NavBar_6` and `NavBar_9` so those instances use the component default.

- [ ] **Step 3: Run validator**

Run: `python scripts/validate_powerapps_source.py`

Expected: still fails only for Archive restore and Table View preview requirements.

### Task 3: Archive Restore Workflow

**Files:**
- Modify: `Inventory_Tracker/Archive.pa.yaml`

- [ ] **Step 1: Track selected archived item**

Set `Table2.OnSelect` to `Set(varArchivedItem, Table2.Selected)`.

- [ ] **Step 2: Add selected item action band**

Add a white, compact horizontal container below the archive search bar with a summary HTML viewer and a disabled-until-selected restore button.

- [ ] **Step 3: Add guarded restore formula**

Use `IfError` around `Patch(Inventories, Defaults(Inventories), {...})`, collect restore errors, and only call `Remove(Expireds, varArchivedItem)` when the restore succeeds.

- [ ] **Step 4: Run validator**

Run: `python scripts/validate_powerapps_source.py`

Expected: still fails only for Table View preview requirements.

### Task 4: Table View Preview

**Files:**
- Modify: `Inventory_Tracker/Table_View.pa.yaml`

- [ ] **Step 1: Change table selection behavior**

Set `Table1.OnSelect` to store `Table1.Selected` in `varTableViewItem` instead of navigating immediately.

- [ ] **Step 2: Add selected preview/action band**

Add a compact preview HTML viewer and an `OpenInventoryButton_TableView` button below the search bar.

- [ ] **Step 3: Add explicit open action**

The open button sets `varIsItemSelected`, `varEditMode`, and `varCurrentItem`, then navigates to `Inventory`. It is disabled when no row is selected.

- [ ] **Step 4: Run validator**

Run: `python scripts/validate_powerapps_source.py`

Expected: exits `0` with `Power Apps source validation OK`.

### Task 5: Final Verification

**Files:**
- Read: all modified files

- [ ] **Step 1: Run validator**

Run: `python scripts/validate_powerapps_source.py`

Expected: exits `0`.

- [ ] **Step 2: Run whitespace check**

Run: `git diff --check`

Expected: exits `0`; CRLF warnings are acceptable if no whitespace errors are reported.

- [ ] **Step 3: Review diff**

Run: `git diff --stat`

Expected: only docs, validator, and intended Power Apps YAML files changed.
