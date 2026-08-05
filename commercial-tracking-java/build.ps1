param(
    [switch]$SkipTests,
    [switch]$SkipFrontend,
    [string]$OutputDirectory = "dist",
    [string]$Version = "0.2.0-rc1"
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$buildRoot = Join-Path $projectRoot "build"
$classes = Join-Path $buildRoot "classes"
$testClasses = Join-Path $buildRoot "test-classes"
$dist = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    [System.IO.Path]::GetFullPath($OutputDirectory)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $projectRoot $OutputDirectory))
}
if (-not $dist.StartsWith($projectRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "OutputDirectory must be inside the project root."
}

if (-not $SkipFrontend) {
    $frontendRoot = Join-Path $projectRoot "frontend"
    if (-not (Test-Path (Join-Path $frontendRoot "node_modules"))) {
        throw "Frontend dependencies are missing. Run npm install in frontend, or use -SkipFrontend with existing compiled assets."
    }
    Push-Location $frontendRoot
    try {
        if (-not $SkipTests) {
            & npm test
            if ($LASTEXITCODE -ne 0) { throw "Frontend tests failed." }
        }
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw "Frontend build failed." }
    } finally {
        Pop-Location
    }
}

if (Test-Path $buildRoot) {
    Remove-Item -LiteralPath $buildRoot -Recurse -Force
}
if (Test-Path $dist) {
    Remove-Item -LiteralPath $dist -Recurse -Force
}
New-Item -ItemType Directory -Path $classes | Out-Null
New-Item -ItemType Directory -Path $testClasses | Out-Null
New-Item -ItemType Directory -Path $dist | Out-Null

$mainSources = Get-ChildItem -Path (Join-Path $projectRoot "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
& javac --release 8 -encoding UTF-8 -d $classes $mainSources
if ($LASTEXITCODE -ne 0) { throw "Main compilation failed." }

$resources = Join-Path $projectRoot "src\main\resources"
if (Test-Path $resources) {
    Copy-Item -Path (Join-Path $resources "*") -Destination $classes -Recurse -Force
}

$testSources = Get-ChildItem -Path (Join-Path $projectRoot "src\test\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
& javac --release 8 -encoding UTF-8 -cp $classes -d $testClasses $testSources
if ($LASTEXITCODE -ne 0) { throw "Test compilation failed." }

if (-not $SkipTests) {
    & java -cp "$classes;$testClasses" org.commercialtracking.ParserTest
    if ($LASTEXITCODE -ne 0) { throw "Parser tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.JsonFlatTest
    if ($LASTEXITCODE -ne 0) { throw "JSON request tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.EventStoreTest
    if ($LASTEXITCODE -ne 0) { throw "Event store tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.ManifestWriterTest
    if ($LASTEXITCODE -ne 0) { throw "Manifest writer tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.ReportWriterTest
    if ($LASTEXITCODE -ne 0) { throw "Report writer tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.SharedConfigManagerTest
    if ($LASTEXITCODE -ne 0) { throw "Shared settings tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.ProjectionTest
    if ($LASTEXITCODE -ne 0) { throw "Projection tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.AddressBookStoreTest
    if ($LASTEXITCODE -ne 0) { throw "Address book tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.AppConfigTest
    if ($LASTEXITCODE -ne 0) { throw "Application configuration tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.PortablePdfTest
    if ($LASTEXITCODE -ne 0) { throw "Portable PDF tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.QrCodeTest
    if ($LASTEXITCODE -ne 0) { throw "QR code tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.DocxWriterTest
    if ($LASTEXITCODE -ne 0) { throw "DOCX writer tests failed." }
    & java -cp "$classes;$testClasses" org.commercialtracking.PerformanceSmokeTest
    if ($LASTEXITCODE -ne 0) { throw "Performance smoke tests failed." }
}

$manifest = Join-Path $buildRoot "MANIFEST.MF"
@(
    "Manifest-Version: 1.0"
    "Main-Class: org.commercialtracking.CommercialTrackingApp"
    "Implementation-Title: Commercial Tracking RC"
    "Implementation-Version: $Version"
    ""
) | Set-Content -LiteralPath $manifest -Encoding ascii

$jar = Join-Path $dist "CommercialTracking-RC.jar"
$jarCommand = Get-Command jar -ErrorAction SilentlyContinue
if ($null -ne $jarCommand) {
    $jarTool = $jarCommand.Source
} else {
    $javaSettings = (& java -XshowSettings:properties -version 2>&1 | Out-String)
    $javaHomeMatch = [regex]::Match($javaSettings, "java\.home\s*=\s*(.+)")
    if (-not $javaHomeMatch.Success) { throw "Could not locate the JDK jar tool." }
    $javaHomePath = $javaHomeMatch.Groups[1].Value.Trim()
    $jarTool = Join-Path $javaHomePath "bin\jar.exe"
}
& $jarTool cfm $jar $manifest -C $classes .
if ($LASTEXITCODE -ne 0) { throw "JAR packaging failed." }

Copy-Item -LiteralPath (Join-Path $projectRoot "run-commercial-tracking.cmd") -Destination $dist
Copy-Item -LiteralPath (Join-Path $projectRoot "TESTING.md") -Destination $dist
Copy-Item -LiteralPath (Join-Path $projectRoot "RELEASE_NOTES.md") -Destination $dist
Copy-Item -LiteralPath (Join-Path $projectRoot "README.md") -Destination $dist
Copy-Item -LiteralPath (Join-Path $projectRoot "qualification") -Destination $dist -Recurse
Write-Host "Built: $jar"
