# Release Notes

## 0.1.0
- Event-sourced medical-supply tracker on a OneDrive-synchronized folder (Java 8, no Maven/Gradle).
- GS1 barcode decoding (AI 01/17/10/21/30) with FNC1 handling.
- Catalog + inventory projections; expiry color coding; PAR + consumption reorder advisor.
- FDA GUDID-backed registration (offline-first, best-effort, cached in the shared catalog).
- Management dashboard and exportable HTML/CSV/PDF report.
- React/MUI browser UI with QR label printing; Swing `--classic-ui` fallback.
