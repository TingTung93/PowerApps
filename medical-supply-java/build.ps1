param([switch]$SkipTests, [string]$OutputDirectory = "dist")
$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$buildRoot = Join-Path $projectRoot "build"
$classes = Join-Path $buildRoot "classes"
$testClasses = Join-Path $buildRoot "test-classes"
$dist = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) { [System.IO.Path]::GetFullPath($OutputDirectory) } else { [System.IO.Path]::GetFullPath((Join-Path $projectRoot $OutputDirectory)) }
if (Test-Path $buildRoot) { Remove-Item -LiteralPath $buildRoot -Recurse -Force }
if (Test-Path $dist) { Remove-Item -LiteralPath $dist -Recurse -Force }
New-Item -ItemType Directory -Path $classes | Out-Null
New-Item -ItemType Directory -Path $testClasses | Out-Null
New-Item -ItemType Directory -Path $dist | Out-Null
$mainSources = Get-ChildItem -Path (Join-Path $projectRoot "src\main\java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 8 -encoding UTF-8 -d $classes $mainSources
if ($LASTEXITCODE -ne 0) { throw "Main compilation failed." }
$testJavaRoot = Join-Path $projectRoot "src\test\java"
if (Test-Path $testJavaRoot) {
    $testSources = Get-ChildItem -Path $testJavaRoot -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    & javac --release 8 -encoding UTF-8 -cp $classes -d $testClasses $testSources
    if ($LASTEXITCODE -ne 0) { throw "Test compilation failed." }
    if (-not $SkipTests) {
        $tests = Get-ChildItem -Path $testClasses -Recurse -Filter *Test.class | ForEach-Object { $_.FullName.Substring($testClasses.Length + 1).Replace('\','.').Replace('.class','') }
        foreach ($t in $tests) { & java -cp "$classes;$testClasses" $t; if ($LASTEXITCODE -ne 0) { throw "Test failed: $t" } }
    }
}
$manifest = Join-Path $buildRoot "MANIFEST.MF"
@("Manifest-Version: 1.0", "Main-Class: org.medsupply.MedicalSupplyApp", "Implementation-Title: Medical Supply RC", "Implementation-Version: 0.1.0-foundation", "") | Set-Content -LiteralPath $manifest -Encoding ascii
$jar = Join-Path $dist "MedicalSupply-RC.jar"
$jarCommand = Get-Command jar -ErrorAction SilentlyContinue
if ($null -ne $jarCommand) { $jarTool = $jarCommand.Source } else {
    $javaSettings = (& java -XshowSettings:properties -version 2>&1 | Out-String)
    $javaHomeMatch = [regex]::Match($javaSettings, "java\.home\s*=\s*(.+)")
    if (-not $javaHomeMatch.Success) { throw "Could not locate the JDK jar tool." }
    $jarTool = Join-Path $javaHomeMatch.Groups[1].Value.Trim() "bin\jar.exe"
}
& $jarTool cfm $jar $manifest -C $classes .
if ($LASTEXITCODE -ne 0) { throw "JAR packaging failed." }
Copy-Item -LiteralPath (Join-Path $projectRoot "run-medical-supply.cmd") -Destination $dist
Write-Host "Built: $jar"
