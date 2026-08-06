# Two-workstation and OneDrive qualification

Use two distinct device IDs and the same empty synchronized pilot root.

| Test | Expected | Result/evidence |
|---|---|---|
| Receive on A, observe on B | B observes immutable event after synchronization | |
| Release on B | A converges to the same projected state | |
| Pause sync, receive on A | Durable local/pending status is explicit | |
| Resume sync | Pending event finalizes once and B observes it | |
| Concurrent receive of same tracking | Both events retained; conflict/duplicate visible | |
| Concurrent release of same revision | Conflict queue shows both actors/devices/times | |
| Resolve conflict | Both clients converge after resolution event | |
| Malformed JSON introduced | File is ignored, preserved, and reported | |
| Delete local cache | Cache rebuilds from shared and pending events | |
| OneDrive conflict-copy check | No conflict copies for unique event filenames | |

Pass criteria: identical projected state after convergence, no changed or
missing finalized events, no operation reported as server-uploaded, and every
unsafe concurrency case remains visible and recoverable.
