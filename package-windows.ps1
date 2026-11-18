param(
    [string]$Version = '2.0.1',
    [switch]$Test,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if ($env:OS -ne 'Windows_NT' -or -not [Environment]::Is64BitOperatingSystem) {
    throw 'The Windows x64 package must be built on 64-bit Windows.'
}
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Use a version such as 2.0.1.' }
$projectDir = $PSScriptRoot
$buildRoot = [IO.Path]::GetFullPath((Join-Path $projectDir 'build'))
$toolchainRoot = Join-Path $buildRoot 'toolchains'
$pin = Get-Content -Raw -LiteralPath (Join-Path $projectDir 'tools/temurin-windows-x64.json') | ConvertFrom-Json
New-Item -ItemType Directory -Path $toolchainRoot -Force | Out-Null

function Get-VerifiedDownload([string]$Url, [string]$Destination, [string]$ExpectedHash) {
    if (-not (Test-Path -LiteralPath $Destination)) {
        Write-Output ('Downloading ' + [IO.Path]::GetFileName($Destination))
        Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
    }
    if ((Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash.ToLowerInvariant() -ne $ExpectedHash) {
        throw "Checksum mismatch: $Destination. Remove this cached file before trying again."
    }
}

function Reset-GeneratedDirectory([string]$Directory) {
    $resolved = [IO.Path]::GetFullPath($Directory)
    $allowed = $buildRoot + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean a directory outside build: $resolved"
    }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
    New-Item -ItemType Directory -Path $resolved -Force | Out-Null
}

$jdkArchive = Join-Path $toolchainRoot $pin.archive
Get-VerifiedDownload $pin.url $jdkArchive $pin.sha256
$jdkExtract = Join-Path $toolchainRoot ('temurin-' + $pin.javaVersion)
$jdkDir = Join-Path $jdkExtract $pin.directory
if (-not (Test-Path -LiteralPath (Join-Path $jdkDir 'bin/jlink.exe'))) {
    Expand-Archive -LiteralPath $jdkArchive -DestinationPath $jdkExtract -Force
}
$jdkRelease = Get-Content -Raw -LiteralPath (Join-Path $jdkDir 'release')
if ($jdkRelease -notmatch 'IMPLEMENTOR="Eclipse Adoptium"' -or
    $jdkRelease -notmatch ('JAVA_VERSION="' + [regex]::Escape($pin.javaVersion) + '"')) {
    throw 'The cached toolchain does not match the pinned Eclipse Temurin version.'
}

if (-not $SkipBuild) {
    $previousJavaHome = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = $jdkDir
        & (Join-Path $projectDir 'build.ps1') -Test:$Test
    } finally { $env:JAVA_HOME = $previousJavaHome }
}
$gameJar = Join-Path $buildRoot 'true-adventure.jar'
if (-not (Test-Path -LiteralPath $gameJar)) { throw 'Build the game JAR first.' }

$workRoot = Join-Path $buildRoot ('packaging/' + $Version)
$releaseRoot = Join-Path $buildRoot ('releases/' + $Version)
Reset-GeneratedDirectory $workRoot
Reset-GeneratedDirectory $releaseRoot
$inputDir = Join-Path $workRoot 'input'
New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
Copy-Item -LiteralPath $gameJar -Destination (Join-Path $inputDir 'game.jar')
$runtime = Join-Path $workRoot 'runtime'
& (Join-Path $jdkDir 'bin/jlink.exe') --add-modules java.desktop --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtime
if ($LASTEXITCODE -ne 0) { throw 'Runtime creation failed.' }
$imageRoot = Join-Path $workRoot 'image'
& (Join-Path $jdkDir 'bin/jpackage.exe') --type app-image --name 'True Adventure' --app-version $Version --vendor 'aliahmed235' --description 'A five-map pixel adventure' --input $inputDir --main-jar game.jar --main-class main.Main --runtime-image $runtime --dest $imageRoot --java-options '-Dfile.encoding=UTF-8'
if ($LASTEXITCODE -ne 0) { throw 'Windows application packaging failed.' }
$appDir = Join-Path $imageRoot 'True Adventure'
$utf8 = [Text.UTF8Encoding]::new($false)
$playInstructions = @"
TRUE ADVENTURE $Version
Windows 10/11, 64-bit

HOW TO PLAY
1. Extract the complete ZIP file.
2. Open the 'True Adventure' folder.
3. Double-click 'True Adventure.exe'.

Java is included. You do not need to install Java or run an installer.
Keep the app and runtime folders beside the EXE.

CONTROLS
WASD or arrows: Move
E: Use, talk, open, or push
Space: Attack or use the selected tool
Tab: Change tools
Shift: Block with the shield
R: Fireball after learning it
Q: Drink a potion
Backspace: Reset rocks
I: Inventory
M: Map
H: Help
Esc: Pause
F11: Fullscreen
F: Sound on/off

Enter your name to start. Progress lasts while the game is open.

Project: https://github.com/aliahmed235/2D-Adventure-Game
"@
[IO.File]::WriteAllText((Join-Path $appDir 'READ ME FIRST.txt'), $playInstructions, $utf8)
$runtimeNotice = @"
BUNDLED JAVA RUNTIME

This application contains an unmodified-module runtime built with jlink from
Eclipse Temurin OpenJDK $($pin.version) for Windows x64.
Only the java.desktop module and its required modules are included.

Runtime licenses and notices are retained in runtime/legal.
The Temurin LICENSE and NOTICE files, where supplied, are in runtime/.
The Java Classpath Exception applies to the relevant OpenJDK components.

Upstream binary release: $($pin.upstreamRelease)
Verified toolchain SHA-256: $($pin.sha256)
Corresponding upstream source: $($pin.sourceUrl)
Source SHA-256: $($pin.sourceSha256)

Matching source archive: $($pin.sourceArchive)
Project-hosted source download: $($pin.distributedSourceUrl)
The game release notes also link to this matching source archive.

The packaging recipe and game source are available in the project repository.
No changes were made to OpenJDK source code.
"@
[IO.File]::WriteAllText((Join-Path $appDir 'RUNTIME-NOTICES.txt'), $runtimeNotice, $utf8)
foreach ($notice in @('LICENSE', 'NOTICE', 'DISCLAIMER')) {
    $source = Join-Path $jdkDir $notice
    if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination (Join-Path $appDir ('runtime/' + $notice)) -Force }
}
$sourceCommit = (& git -C $projectDir rev-parse HEAD).Trim()
$buildInfo = [ordered]@{
    version = $Version
    platform = 'windows-x64'
    sourceCommit = $sourceCommit
    gameJarSha256 = (Get-FileHash -LiteralPath $gameJar -Algorithm SHA256).Hash.ToLowerInvariant()
    runtime = 'Eclipse Temurin ' + $pin.version
    runtimeToolchainSha256 = $pin.sha256
    javaInstallRequired = $false
}
[IO.File]::WriteAllText((Join-Path $appDir 'build-info.json'), ($buildInfo | ConvertTo-Json), $utf8)

if ($Test) {
    $testClasses = Join-Path $buildRoot 'test-classes'
    if (-not (Test-Path -LiteralPath (Join-Path $testClasses 'tests/GameTests.class'))) {
        throw 'Run build.ps1 -Test before using package-windows.ps1 -Test -SkipBuild.'
    }
    & (Join-Path $projectDir 'tools/test-windows-package.ps1') -AppImagePath $appDir -NativeWindows
}

$zipName = 'True-Adventure-' + $Version + '-Windows-x64.zip'
$jarName = 'True-Adventure-' + $Version + '.jar'
Compress-Archive -LiteralPath $appDir -DestinationPath (Join-Path $releaseRoot $zipName) -CompressionLevel Optimal
Copy-Item -LiteralPath $gameJar -Destination (Join-Path $releaseRoot $jarName)
$sourceArchive = Join-Path $toolchainRoot $pin.sourceArchive
Get-VerifiedDownload $pin.sourceUrl $sourceArchive $pin.sourceSha256
Copy-Item -LiteralPath $sourceArchive -Destination (Join-Path $releaseRoot $pin.sourceArchive)
$checksumLines = @($zipName, $jarName, $pin.sourceArchive) | ForEach-Object {
    (Get-FileHash -LiteralPath (Join-Path $releaseRoot $_) -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $_
}
[IO.File]::WriteAllLines((Join-Path $releaseRoot 'SHA256SUMS.txt'), $checksumLines, $utf8)
Write-Output "Release files: $releaseRoot"
Get-ChildItem -LiteralPath $releaseRoot -File | ForEach-Object { Write-Output ('{0} ({1:N1} MB)' -f $_.Name, ($_.Length / 1MB)) }
