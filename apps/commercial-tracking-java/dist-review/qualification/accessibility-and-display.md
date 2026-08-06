# Accessibility and display qualification

Test Edge and the approved workstation browser at 1366×768 and 1920×1080,
Windows scaling 100%, 125%, and 150%, and browser zoom 90%, 100%, and 125%.

| Test | Expected | Result/evidence |
|---|---|---|
| Keyboard-only receive/release | Logical focus order; visible focus; no trap | |
| Dialog Escape/Cancel | No state change; focus restored | |
| Screen reader scan result | Completion/result announced, not each character | |
| Table navigation and headers | Headers announced; rows keyboard accessible | |
| Reduced motion | Nonessential motion removed | |
| Sound disabled | All state remains visually distinguishable | |
| 1366×768 receive view | Input, result, and three recent rows visible | |
| Below 900 px | Rail collapses and detail content remains usable | |
| Contrast audit | WCAG 2.1 AA for text and controls | |

Record the assistive technology and version. Any inaccessible primary workflow
is a release blocker.
