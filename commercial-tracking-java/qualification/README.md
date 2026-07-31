# Commercial Tracking qualification

Production approval requires completed evidence from each worksheet:

1. `scanner-qualification.md`
2. `two-workstation-onedrive.md`
3. `accessibility-and-display.md`
4. `performance-qualification.md`

Record workstation model, Windows version, Java version, browser version,
scanner make/model/firmware, OneDrive version, operator, date, result, and
evidence location. Failures are release blockers unless an owner accepts a
documented deviation.

Repository-controlled automated evidence is produced by:

```powershell
.\build.ps1 -OutputDirectory dist-review
java -jar .\dist-review\CommercialTracking-RC.jar --self-test
```
