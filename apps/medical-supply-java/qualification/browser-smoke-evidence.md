# Browser Smoke Evidence

| Step | Expected | Result | Notes |
|------|----------|--------|-------|
| Launch JAR | Prints `Medical Supply UI: http://127.0.0.1:<port>`, browser opens | | |
| Set folder | Dashboard renders, no errors | | |
| OS identity | Settings shows signed-in Windows user and it cannot be edited | | |
| Scan known GTIN | Metadata review appears; cursor advances to quantity; item is received | | |
| Scan unknown GTIN | Inline registration details appear (GUDID prefill when online) | | |
| Two-scan receive | Primary GTIN plus production-data scan resolves lot/expiration | | |
| Negative guard | Picking more than on-hand is rejected and quantity is unchanged | | |
| Zero auto-archive | Picking the final unit archives the lot | | |
| Archive/restore | Archived lot is searchable and restore creates a history entry | | |
| Catalog controls | Edit and retire actions work without browser prompts | | |
| Filters | Manufacturer and category filters narrow Inventory | | |
| Distro management | Valid addresses save and replay on another refresh | | |
| Incomplete trail | Corrupt test event shows persistent warning and blocks report | | |
| Pending recovery | Buffered event retries and pending count returns to zero | | |
| Expiry coloring | ≤7d red, ≤30d amber | | |
| Export report | HTML/CSV/PDF written under `reports/` | | |
| Print labels | QR labels render and print | | |
| `--classic-ui` | Swing window works offline | | |

Java version: ______  Date: ______  Tester: ______

Facility / workstation: ____________________  RC artifact SHA-256: ______________________________

Tester signature: __________________________________  Date: __________
