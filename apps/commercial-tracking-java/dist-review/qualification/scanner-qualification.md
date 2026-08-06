# Scanner qualification

Use the exact deployed scanner/workstation combinations and approved synthetic
labels. Run the guided scanner test first and save its recommendation.

| Test | Expected | Result/evidence |
|---|---|---|
| Enter suffix, 10 consecutive scans | One event per physical scan; focus restored | |
| Tab suffix, 10 consecutive scans | One event per physical scan; no navigation leak | |
| No suffix, 10 consecutive scans | One event per scan after quiet interval | |
| Manual typing with pauses | No automatic submission | |
| Paste complete payload | One complete submission | |
| Edit captured value | Pending automatic submission cancels | |
| Long 2D ANSI/MH10 | No truncation; separators preserved | |
| GS1 with group separators | Correct fields and separator preservation | |
| Ambiguous label | Confirmation shown; cancel creates no event | |
| Rapid duplicate physical scan | No unconfirmed duplicate receive | |

Pass criteria: zero lost, truncated, or duplicate submissions; the scanner test
detects the terminator and recommends usable timing; all displayed tracking
values match the physical labels.
