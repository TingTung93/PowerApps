param(
    [switch]$SkipTests,
    [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$buildRoot = Join-Path $projectRoot "build"
$classes = Join-Path $buildRoot "classes"
$testClasses = Join-Path $buildRoot "test-classes"
$dist = Join-Path $projectRoot "dist"

if (-not $SkipFrontend) {
    $frontendRoot = Join-Path $projectRoot "frontend"
    if (-not (Test-Path (Join-Path $frontendRoot "node_modules"))) {
        throw "Frontend dependencies are missing. Run npm install in frontend, or use -SkipFrontend with existing compiled assets."
    }
    Push-Location $frontendRoot
    try {
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
    & java -cp "$classes;$testClasses" org.commercialtracking.EventStoreTest
    if ($LASTEXITCODE -ne 0) { throw "Event store tests failed." }
}

$manifest = Join-Path $buildRoot "MANIFEST.MF"
@(
    "Manifest-Version: 1.0"
    "Main-Class: org.commercialtracking.CommercialTrackingApp"
    "Implementation-Title: Commercial Tracking RC"
    "Implementation-Version: 0.2.0-rc1"
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
Write-Host "Built: $jar"
