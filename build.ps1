param([switch]$Test, [switch]$Run)
$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot

# Prefer a real JDK over Windows' java-path launcher shim.
$jdkCandidates = @()
if ($env:JAVA_HOME) { $jdkCandidates += $env:JAVA_HOME }
$jdkCandidates += @(Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles 'Java') -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | ForEach-Object { $_.FullName })
$jdkDir = $jdkCandidates | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin/javac.exe') } | Select-Object -First 1
if ($jdkDir) {
    $compiler = Join-Path $jdkDir 'bin/javac.exe'
    $java = Join-Path $jdkDir 'bin/java.exe'
    $jar = Join-Path $jdkDir 'bin/jar.exe'
} else {
    $compiler = (Get-Command javac -ErrorAction Stop).Source
    $java = (Get-Command java -ErrorAction Stop).Source
    $jar = (Get-Command jar -ErrorAction Stop).Source
}

$buildDir = Join-Path $projectDir 'build'
$classesDir = Join-Path $buildDir 'classes'
$testDir = Join-Path $buildDir 'test-classes'
foreach ($generatedDir in @($classesDir, $testDir)) {
    $resolvedDir = [IO.Path]::GetFullPath($generatedDir)
    $allowedRoot = [IO.Path]::GetFullPath($buildDir) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedDir.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean a directory outside build: $resolvedDir"
    }
    if (Test-Path -LiteralPath $resolvedDir) { Remove-Item -LiteralPath $resolvedDir -Recurse -Force }
    New-Item -ItemType Directory -Path $resolvedDir -Force | Out-Null
}

$sourceList = Join-Path $buildDir 'sources.txt'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectDir 'src') -Filter '*.java' -Recurse |
    Sort-Object FullName | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
[IO.File]::WriteAllLines($sourceList, $sources, [Text.UTF8Encoding]::new($false))
& $compiler --release 17 -encoding UTF-8 -d $classesDir "@$sourceList"
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed. Install JDK 17 or newer.' }

if ($Test) {
    $testList = Join-Path $buildDir 'test-sources.txt'
    $testSources = @(Get-ChildItem -LiteralPath (Join-Path $projectDir 'tests') -Filter '*.java' -Recurse |
        Sort-Object FullName | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
    [IO.File]::WriteAllLines($testList, $testSources, [Text.UTF8Encoding]::new($false))
    $testClasspath = "$classesDir;$(Join-Path $projectDir 'res');$(Join-Path $projectDir 'tests/resources')"
    & $compiler --release 17 -encoding UTF-8 -cp $testClasspath -d $testDir "@$testList"
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    Push-Location $projectDir
    try { & $java '-Djava.awt.headless=true' -cp "$testDir;$testClasspath" tests.GameTests }
    finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { throw 'Game tests failed.' }
}

$artifact = Join-Path $buildDir 'sunken-seal.jar'
& $jar --create --file $artifact --main-class main.Main -C $classesDir . -C (Join-Path $projectDir 'res') .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
Copy-Item -LiteralPath $artifact -Destination (Join-Path $projectDir 'game.jar') -Force
if (Test-Path -LiteralPath (Join-Path $projectDir 'dist')) {
    Copy-Item -LiteralPath $artifact -Destination (Join-Path $projectDir 'dist/game.jar') -Force
    $launcherCompiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
    if (Test-Path -LiteralPath $launcherCompiler) {
        $launcherOutput = Join-Path $projectDir 'dist/2DAdventureGame.exe'
        & $launcherCompiler /nologo /target:winexe /optimize+ "/out:$launcherOutput" /reference:System.Windows.Forms.dll (Join-Path $projectDir 'tools/WindowsLauncher.cs')
        if ($LASTEXITCODE -ne 0) { throw 'Windows launcher compilation failed.' }
    }
}
Write-Output "Built $artifact"
if ($Run) { & $java -jar $artifact }
