[CmdletBinding()]
param(
    [string]$CommercialTrackingVersion,
    [string]$MedicalSupplyVersion,
    [string]$OutputDirectory = "releases",
    [switch]$SkipTests,
    [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"
$repoRoot = $PSScriptRoot
$trackPath = Join-Path $repoRoot 'java-release-track.json'
if (-not (Test-Path -LiteralPath $trackPath)) { throw "Release track is missing: $trackPath" }
$track = Get-Content -LiteralPath $trackPath -Raw | ConvertFrom-Json
if ($track.schemaVersion -ne 1) { throw "Unsupported java-release-track.json schema version." }
if (-not $CommercialTrackingVersion) { $CommercialTrackingVersion = $track.applications.commercialTracking.version }
if (-not $MedicalSupplyVersion) { $MedicalSupplyVersion = $track.applications.medicalSupply.version }
$outputRoot = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
    [IO.Path]::GetFullPath($OutputDirectory)
} else {
    [IO.Path]::GetFullPath((Join-Path $repoRoot $OutputDirectory))
}

function Assert-Version([string]$Version) {
    if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?$') {
        throw "Invalid release version '$Version'. Use a value such as 1.2.3 or 1.2.3-rc1."
    }
}

function Assert-Channel([string]$Version, [string]$Channel) {
    if ($Channel -notin @('candidate', 'stable')) { throw "Unsupported release channel '$Channel'." }
    $isPrerelease = $Version.Contains('-')
    if ($Channel -eq 'stable' -and $isPrerelease) { throw "Stable releases cannot use a pre-release version: $Version" }
    if ($Channel -eq 'candidate' -and -not $isPrerelease) { throw "Candidate releases must use a pre-release version: $Version" }
}

function Write-Utf8File([string]$Path, [string[]]$Lines) {
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllLines($Path, $Lines, $encoding)
}

function New-Zip([string]$SourceDirectory, [string]$Destination) {
    if (Test-Path -LiteralPath $Destination) { Remove-Item -LiteralPath $Destination -Force }
    Compress-Archive -Path (Join-Path $SourceDirectory '*') -DestinationPath $Destination -CompressionLevel Optimal
}

function New-AppRelease {
    param(
        [string]$Name,
        [string]$Version,
        [string]$Channel,
        [string]$ProjectDirectory,
        [string]$JarName,
        [string]$LauncherName
    )

    Assert-Version $Version
    Assert-Channel $Version $Channel
    $projectRoot = Join-Path $repoRoot $ProjectDirectory
    $buildArgs = @{ OutputDirectory = 'dist-release'; Version = $Version }
    if ($SkipTests) { $buildArgs.SkipTests = $true }
    if ($SkipFrontend) { $buildArgs.SkipFrontend = $true }
    # Keep build diagnostics visible without allowing success-stream output from
    # child tools to become elements in this function's return value.
    & (Join-Path $projectRoot 'build.ps1') @buildArgs | Out-Host

    $dist = Join-Path $projectRoot 'dist-release'
    foreach ($required in @($JarName, $LauncherName, 'README.md', 'RELEASE_NOTES.md')) {
        if (-not (Test-Path -LiteralPath (Join-Path $dist $required))) {
            throw "Build did not produce required artifact: $required"
        }
    }

    $releaseName = "$Name-$Version"
    $workRoot = Join-Path $projectRoot 'build\release-package'
    $workstation = Join-Path $workRoot $releaseName
    $deployment = Join-Path $workRoot "$releaseName-deployment"
    if (Test-Path -LiteralPath $workRoot) { Remove-Item -LiteralPath $workRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $workstation, $deployment | Out-Null

    Copy-Item -LiteralPath (Join-Path $dist $JarName) -Destination $workstation
    Copy-Item -LiteralPath (Join-Path $dist $LauncherName) -Destination $workstation
    Copy-Item -LiteralPath (Join-Path $dist 'RELEASE_NOTES.md') -Destination $workstation
    Write-Utf8File (Join-Path $workstation 'START-HERE.txt') @(
        "$Name $Version"
        "Release channel: $Channel"
        ""
        "1. Extract this entire ZIP to a local folder."
        "2. Confirm Java 8 or newer is installed: java -version"
        "3. Double-click $LauncherName."
        "4. On first run, select the approved synchronized data folder."
        ""
        "Keep the JAR and launcher together. Do not run the application inside the ZIP."
        "For support, provide the application name, version, and this folder location."
    )

    New-Item -ItemType Directory -Path (Join-Path $deployment 'workstation-package'), (Join-Path $deployment 'documentation') | Out-Null
    $workstationZip = Join-Path $outputRoot "$releaseName-workstation.zip"
    New-Zip $workstation $workstationZip
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $workstationZip).Hash.ToLowerInvariant()
    Write-Utf8File "$workstationZip.sha256" @("$hash  $([IO.Path]::GetFileName($workstationZip))")
    Copy-Item -LiteralPath $workstationZip, "$workstationZip.sha256" -Destination (Join-Path $deployment 'workstation-package')
    Copy-Item -LiteralPath (Join-Path $dist 'README.md'), (Join-Path $dist 'RELEASE_NOTES.md') -Destination (Join-Path $deployment 'documentation')
    foreach ($optional in @('TESTING.md', 'qualification')) {
        $source = Join-Path $dist $optional
        if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination (Join-Path $deployment 'documentation') -Recurse }
    }
    Write-Utf8File (Join-Path $deployment 'DEPLOYMENT.txt') @(
        "$Name $Version deployment kit"
        "Release channel: $Channel"
        ""
        "Distribute only the ZIP in workstation-package to end users."
        "Verify its SHA-256 hash against the adjacent .sha256 file before deployment."
        "Extract the workstation ZIP on each computer; do not copy this deployment kit itself."
        "Requirements: Windows, Java 8+, and a modern browser. No Node/npm or administrator rights are required."
        "After extraction, run: $LauncherName"
        "Optional validation: java -jar $JarName --self-test"
        "Deployment and qualification references are under documentation."
    )

    $deploymentZip = Join-Path $outputRoot "$releaseName-deployment-kit.zip"
    New-Zip $deployment $deploymentZip
    $deploymentHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $deploymentZip).Hash.ToLowerInvariant()
    Write-Utf8File "$deploymentZip.sha256" @("$deploymentHash  $([IO.Path]::GetFileName($deploymentZip))")

    [pscustomobject]@{
        Application = $Name
        Version = $Version
        Channel = $Channel
        WorkstationPackage = [IO.Path]::GetFileName($workstationZip)
        WorkstationSha256 = $hash
        DeploymentKit = [IO.Path]::GetFileName($deploymentZip)
        DeploymentSha256 = $deploymentHash
    }
}

New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$results = @(
    New-AppRelease -Name 'CommercialTracking' -Version $CommercialTrackingVersion -Channel $track.applications.commercialTracking.channel -ProjectDirectory 'commercial-tracking-java' -JarName 'CommercialTracking-RC.jar' -LauncherName 'run-commercial-tracking.cmd'
    New-AppRelease -Name 'MedicalSupply' -Version $MedicalSupplyVersion -Channel $track.applications.medicalSupply.channel -ProjectDirectory 'medical-supply-java' -JarName 'MedicalSupply-RC.jar' -LauncherName 'run-medical-supply.cmd'
)

$manifest = @("Java application release artifacts", "Generated: $([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))", "")
foreach ($result in $results) {
    $manifest += "$($result.Application) $($result.Version) [$($result.Channel)]"
    $manifest += "  End-user/workstation: $($result.WorkstationPackage)"
    $manifest += "  SHA-256: $($result.WorkstationSha256)"
    $manifest += "  Deployment/support: $($result.DeploymentKit)"
    $manifest += "  SHA-256: $($result.DeploymentSha256)"
    $manifest += ""
}
$manifest = $manifest[0..($manifest.Count - 2)]
Write-Utf8File (Join-Path $outputRoot 'JAVA-RELEASE-MANIFEST.txt') $manifest
$results | Format-Table Application, Version, Channel, WorkstationPackage, DeploymentKit -AutoSize
