# Browser Smoke Evidence

| Step | Expected | Result | Notes |
|------|----------|--------|-------|
| Launch JAR | Prints `Medical Supply UI: http://127.0.0.1:<port>`, browser opens | | |
| Set folder | Dashboard renders, no errors | | |
| Scan known GTIN | Item received, appears in Inventory | | |
| Scan unknown GTIN | Registration prompt (GUDID prefill when online) | | |
| Expiry coloring | ≤7d red, ≤30d amber | | |
| Export report | HTML/CSV/PDF written under `reports/` | | |
| Print labels | QR labels render and print | | |
| `--classic-ui` | Swing window works offline | | |

Java version: ______  Date: ______  Tester: ______
