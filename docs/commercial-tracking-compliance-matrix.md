# Commercial Tracking compliance matrix

Reviewed: 2026-07-30

Status legend:

- **Implemented/tested** — controlled by this repository and covered by
  automated tests, build verification, or source review.
- **Qualification required** — implemented, but final evidence requires the
  deployed Windows/OneDrive/scanner environment.
- **Architecture boundary** — explicitly excluded or optional in the governing
  specification.

## Product and technical specification

| Spec area | Status | Implementation/evidence |
|---|---|---|
| 5–7 architecture and folder layout | Implemented/tested | Java 8 loopback client, shared immutable files, local config/cache/pending/recovery |
| 8 safe immutable event commit | Implemented/tested | `EventStore`, `.tmp` → `.partial` → finalized JSON, rescan retention, idempotency tests |
| 9–10 versioned event envelope/types | Implemented/tested | `TrackingEvent`, `EventJson`, correction/void/manifest/config/conflict events |
| 11 deterministic projection | Implemented/tested | Four-key ordering, revisioned `Projection`, replay tests |
| 12 duplicate/stale/conflict policy | Implemented/tested | Duplicate confirmation/location change, stale revision blocks, conflict queue/resolution |
| 13 synchronization language | Implemented/tested | Local pending/submitted/observed/attention vocabulary; no server-upload claim |
| 14.1 first run | Implemented/tested | Modal setup, root structure validation/write probe, device/account, cache rebuild |
| 14.2 scanner framing/parsing | Implemented/tested + qualification required | Pure capture state machine and parser tests; physical scanner worksheet required |
| 14.3 receiving/session | Implemented/tested | One-scan receive, remembered location, active session persistence, finish warnings |
| 14.4 release | Implemented/tested | Lookup/verification card, explicit confirm, unsafe-state blocks |
| 14.5 history/reconciliation | Implemented/tested | Multi-field search/filter/timeline, reviewed bulk assignment, correction/void |
| 14.6 manifests | Implemented/tested | Inbound/custody membership, prepared/printed events, checksums, PDF/HTML, register/reprint |
| 14.7 reports | Implemented/tested | Event-time range semantics, filters/layout, PDF/HTML/CSV, temporary/shared output |
| 14.8 recovery/diagnostics | Implemented/tested | Retry, cache rebuild, malformed isolation, clock warnings, redacted export |
| 15 monitoring/cache/performance | Implemented/tested + qualification required | WatchService + reconciliation, checksummed rebuildable index; release-gate 100k projection: 36 ms and 1000 exact lookups: <1 ms; deployed filesystem/OneDrive worksheet required |
| 16 configuration | Implemented/tested | Local workstation settings; validated shared config, audit, last-valid fallback, rollback |
| 17 security/privacy | Implemented/tested | Loopback/token/origin checks, no credentials, redacted diagnostics, sensitive-free filenames |
| 18 deployment | Implemented/tested | Portable Java 8 JAR, offline bundled assets, self-test, no installer/elevation |
| 19–20 reliability/acceptance | Qualification required | Two-workstation, OneDrive, offline, performance, scanner, and accessibility worksheets |
| Carrier API enrichment | Architecture boundary | Optional and disabled; routine workflows have no provider dependency |

## UI/UX specification

| Spec area | Status | Implementation/evidence |
|---|---|---|
| 3–4 shell/navigation/status language | Implemented/tested | Persistent grouped rail, responsive drawer, task headings, plain-language state |
| 5 Receive Packages | Implemented/tested | Location + focused scanner + persistent result + recent session only |
| 6 Release Packages | Implemented/tested | Separate workflow with unambiguous lookup and explicit release |
| 7 Current Session | Implemented/tested | Projected package rows, activity tab, detail panel, finish-session safeguard |
| 8 History | Implemented/tested | Prominent multi-field search, filters, timeline detail |
| 9 Reconciliation | Implemented/tested | Active unassigned queue and reviewed bulk assignment |
| 10 Manifests | Implemented/tested | Purpose, eligible membership, review/readiness, finalize/register/reprint |
| 11 Reports | Implemented/tested | Non-mutating banner, range/filter/layout, live count, PDF/CSV/shared copy |
| 12 Attention/recovery | Implemented/tested | Prioritized durable-save/pending/conflict/malformed notices and safe retry |
| 13 Settings/diagnostics | Implemented/tested | Guided scanner test, workstation/shared scope, rollback, health/export |
| 14–16 dialog/keyboard/visual rules | Implemented/tested | Outcome labels, contextual panels, destructive separation, focus/colour/text rules |
| 17 responsive workstation behavior | Implemented/tested + qualification required | Packaged browser smoke passed at 1366×768 and collapsed navigation at 800×768; Windows scaling/browser matrix remains |
| 18 accessibility | Qualification required | Semantic controls, live regions, tooltips, reduced motion implemented; AT/contrast audit required |
| 19 UX validation targets | Qualification required | Representative-operator and physical-device evidence required |

## Operational decisions applied for the pilot

The specification leaves eight policy choices open. Conservative defaults are:

1. Inbound manifests contain one location.
2. Custody manifests include explicitly selected active packages only.
3. Every release requires confirmation.
4. Recipient suggestions are not retained.
5. Signature/certification wording remains generic until policy approves text.
6. Letter-size local PDF and system printer behavior; shared exports require an explicit action.
7. Closing without a manifest requires an additional confirmation.
8. Shared settings are available to users who already have write access to the synchronized root and every change is audited.

## Release decision

All repository-controlled requirements are implemented; automated gates and the
packaged receive/assign/verified-release browser smoke pass.
Production specification compliance is not complete until the four qualification
worksheets under `apps/commercial-tracking-java/qualification/` contain passing,
reviewed evidence from the target environment.
