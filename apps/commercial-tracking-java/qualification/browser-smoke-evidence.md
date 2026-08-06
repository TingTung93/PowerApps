# Packaged browser smoke evidence

Date: 2026-07-30
Artifact SHA-256: `D3A61C59CC6CAED11EE3C2DE2DAB532BBE8A54A6EADF07E1E61B721C389A8FBB`

The packaged JAR was started with a clean, isolated local profile and empty
shared-root fixture. The browser loaded the embedded production assets from the
loopback-only server.

| Check | Result |
|---|---|
| First-run modal and writable-root configuration | Pass |
| Navigation and semantic accessibility snapshot | Pass |
| UPS receive through JSON HTTP API | Pass |
| Durable event observed in current session | Pass |
| Recipient assignment dialog | Pass |
| Release lookup and package verification card | Pass |
| Explicit confirm release and focus restoration | Pass |
| Responsive permanent rail at 1366×768 | Pass |
| Collapsed navigation control at 800×768 | Pass |
| Current-page JavaScript errors after corrected run | None |

The first run of this smoke test found two defects which were fixed before this
evidence was recorded:

1. Flat request parsing rejected JSON numbers and booleans.
2. Background refresh could reset an open dialog's unsaved value.

Regression coverage was added for JSON primitive parsing; the corrected dialog
was rebuilt and the full browser workflow was repeated successfully.
