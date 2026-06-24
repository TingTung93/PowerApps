# Navbar, Archive, and Table Workflows Design

## Goal

Improve the newly added navigation, Archive, and Table View surfaces so they behave consistently with the rest of the inventory app and support safer daily workflows.

## Scope

- Keep the existing Power Apps YAML structure and control families.
- Treat `NavBarComponent.pa.yaml` as the default navigation source.
- Add a guarded Archive restore action that only removes an archive row after the inventory patch succeeds.
- Add a compact Table View selected-record preview and explicit open action.
- Add source-level validation because this repo does not include a runnable Power Apps test harness.

## Design

The navbar component keeps a complete default `Items` table with Action Center first. New screens no longer need to duplicate that table unless they require a custom menu. The component remains configurable through its `Items` input.

Archive remains a reference screen but gains a selected-record action band. Selecting an archived row stores `varArchivedItem`. The restore button patches a new inventory row from the selected archive record. `IfError` collects restore errors and returns a compatible record value, and `Remove(Expireds, varArchivedItem)` only runs after a successful restore.

Table View remains a dense read-only grid. Selecting a row stores `varTableViewItem`; a preview band displays the selected item name, item ID, batch, quantity, expiration, and category. Users open the record through an explicit button, which sets `varCurrentItem`, `varIsItemSelected`, and `varEditMode` before navigating to Inventory.

## Verification

Run `python scripts/validate_powerapps_source.py`, YAML parse validation, and `git diff --check`. The validator checks for valid YAML, navbar defaults, Archive restore safety patterns, Table View preview behavior, and contextual help/search coverage.
