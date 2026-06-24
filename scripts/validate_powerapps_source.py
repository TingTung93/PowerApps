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

    require(
        '{Label: "Action Center", Screen: ActionCenter}' in navbar,
        "navbar default includes Action Center",
        failures,
    )
    require(
        "TemplateSize:" in navbar and "Parent.Width < 900" in navbar,
        "navbar has responsive template sizing",
        failures,
    )
    require("RestoreButton_Archive" in archive, "archive has a restore button", failures)
    require("varArchivedItem" in archive, "archive tracks selected archived item", failures)
    require(
        "IfError(" in archive and "Remove(Expireds, varArchivedItem)" in archive,
        "archive restore is guarded by IfError before remove",
        failures,
    )
    require("PreviewHtml_TableView" in table_view, "table view has selected item preview", failures)
    require(
        "OpenInventoryButton_TableView" in table_view,
        "table view uses explicit open action",
        failures,
    )
    require("varTableViewItem" in table_view, "table view tracks selected item", failures)

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        return 1

    print("Power Apps source validation OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
