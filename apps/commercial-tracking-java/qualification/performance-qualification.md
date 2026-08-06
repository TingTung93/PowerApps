# Performance qualification

Populate an empty pilot root with representative synthetic immutable events:
100,000 total events across monthly partitions, at least 10,000 active packages,
two devices, manifests, corrections, voids, and conflicts. Do not use production
tracking or recipient data.

Measure after a cold cache rebuild and after a warm restart:

The release build also runs a platform-independent 100,000-event projection
smoke test. Record that output here, but do not substitute it for the filesystem
and OneDrive measurements below.

| Measurement | Target | Result/evidence |
|---|---|---|
| Cold rebuild completes without error | Required | |
| Warm startup to usable Receive view | ≤ 10 seconds | |
| Exact tracking lookup, p95 | ≤ 1 second | |
| Three-character history search, p95 | ≤ 2 seconds | |
| 10-package scan burst | Zero loss/duplicates | |
| Background reconciliation | Scanner remains responsive | |
| Memory after warm load | Record and approve | |

Run for at least 30 minutes while OneDrive is active. Capture timings, event
count, directory count, process memory, and diagnostics export.
