# Java release packages

Use the repository-level release command to create the shareable artifacts for both Java applications:

```powershell
.\package-java-releases.ps1
```

The source of truth is [`java-release-track.json`](java-release-track.json). Each application independently follows one of two channels:

- `candidate` — qualification builds with a SemVer pre-release suffix such as `0.2.0-rc1`.
- `stable` — approved end-user builds with a plain SemVer version such as `0.1.0`.

The packaging command reads its versions and channels from that file. It rejects a stable version with a pre-release suffix and a candidate version without one.

The command builds both frontends, compiles Java 8-compatible JARs, runs the automated tests, and writes these files to `releases/`:

- `*-workstation.zip` — the only package to send to end users. It contains the runnable JAR, launcher, short start instructions, and release notes.
- `*-workstation.zip.sha256` — checksum used by deployment staff to verify the end-user package.
- `*-deployment-kit.zip` — the workstation ZIP and checksum plus testing, qualification, and support documentation.
- `*-deployment-kit.zip.sha256` — checksum for transferring the complete deployment kit.
- `JAVA-RELEASE-MANIFEST.txt` — versions, exact filenames, and hashes for the current build.

The deployment kit deliberately contains the end-user ZIP unchanged. Deployment staff should verify its checksum and distribute that nested ZIP; they should not ask users to choose files from the kit.

## Advancing the track

Update `java-release-track.json` in the same change as the relevant release notes. A normal release follows this progression:

1. Set `version` to the next `x.y.z-rc1` and `channel` to `candidate`.
2. Build, test, and qualify the generated deployment kit.
3. Apply fixes as `-rc2`, `-rc3`, and so on; never replace an already shared version.
4. Promote by removing the suffix and changing `channel` to `stable`.
5. Commit the track file and artifacts, then tag that commit per app: `java/commercial-tracking/v0.2.0` or `java/medical-supply/v0.1.0`.

Version parameters remain available for local packaging diagnostics, but the channel still comes from the track. Published releases must use the versions recorded in `java-release-track.json`.

Use `-SkipFrontend` only when the checked-in compiled web resources are already current. Use `-SkipTests` only for packaging diagnostics, never for a release intended for distribution.

## Workstation deployment

1. Verify the workstation ZIP against its adjacent SHA-256 file.
2. Copy the workstation ZIP to the target computer.
3. Extract the complete ZIP to a local folder.
4. Confirm `java -version` reports Java 8 or newer.
5. Run the included `.cmd` launcher and select the approved synchronized data folder.

Node, npm, the JDK, source code, and qualification documents are not required on end-user computers.
