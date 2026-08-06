# Power Apps and portable Java applications

Maintained applications live under `apps/`:

- `apps/commercial-tracking-java` — portable commercial tracking application
- `apps/medical-supply-java` — portable medical supply application
- `apps/inventory-tracker-powerapps` — current inventory tracker canvas source
- `apps/commercial-tracking-powerapps` — commercial tracking solution source

Older unpacked Power Apps snapshots are retained under `archive/powerapps/` for
reference. Shareable Java artifacts are produced by
`scripts/package-java-releases.ps1`; see
[`docs/java-releases.md`](docs/java-releases.md) for the release and workstation
deployment workflow.
