<#
.SYNOPSIS
Checks a portable Windows app image using only its bundled runtime.
.DESCRIPTION
Runs headless regression tests, then briefly launches and closes this package's
own executable. -NativeWindows also runs the visible Swing/fullscreen checks.
No installed Java, environment changes, or unrelated processes are used.
#>
param(
    [Parameter(Mandatory = $true)][string]$AppImagePath,
    [string]$TestClassesPath = (Join-Path (Split-Path $PSScriptRoot -Parent) 'build/test-classes'),
    [Alias('TestResources')][string]$TestResourcesPath = (Join-Path (Split-Path $PSScriptRoot -Parent) 'tests/resources'),
    [switch]$NativeWindows
)

$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent

function Get-ExistingPath([string]$Path, [string]$Description, [string]$Type) {
    if (-not (Test-Path -LiteralPath $Path -PathType $Type)) {
        throw "Missing ${Description}: $Path"
    }
    return [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $Path).ProviderPath)
}

function Quote-NativeArgument([string]$Value) {
    # Windows command-line quoting, including quotes and trailing backslashes.
    $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
    return '"' + $escaped + '"'
}

function New-IsolatedStartInfo([string]$Executable, [string[]]$Arguments) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $Executable
    $info.Arguments = (($Arguments | ForEach-Object { Quote-NativeArgument $_ }) -join ' ')
    $info.WorkingDirectory = $projectDir
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    foreach ($key in @('JAVA_HOME', 'JDK_HOME', 'JRE_HOME', 'JAVA_TOOL_OPTIONS',
                       '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH')) {
        $info.EnvironmentVariables.Remove($key)
    }
    $windowsDir = [Environment]::GetFolderPath('Windows')
    if ([string]::IsNullOrWhiteSpace($windowsDir)) { $windowsDir = $env:WINDIR }
    if ([string]::IsNullOrWhiteSpace($windowsDir)) { throw 'Cannot locate the Windows system directory.' }
    $info.EnvironmentVariables['PATH'] = (Join-Path $windowsDir 'System32') + ';' + $windowsDir
    return $info
}

function Invoke-BundledJava([string[]]$Arguments, [string]$Check) {
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = New-IsolatedStartInfo $runtimeJava $Arguments
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    $started = $false
    try {
        if (-not $process.Start()) { throw "$Check could not start." }
        $started = $true
        # Drain both pipes while the process runs so verbose failures cannot deadlock.
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $timeout = [Diagnostics.Stopwatch]::StartNew()
        while (-not $process.WaitForExit(1000)) {
            if ($timeout.Elapsed.TotalSeconds -ge 60) {
                $process.Kill()
                $process.WaitForExit(5000) | Out-Null
                throw "$Check exceeded 60 seconds. Only its own test process was stopped."
            }
        }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            throw ("$Check failed (exit $($process.ExitCode)).`n" + $stdout + $stderr)
        }
        return [PSCustomObject]@{ Output = $stdout; ErrorOutput = $stderr }
    } finally {
        if ($started -and -not $process.HasExited) {
            $process.Kill()
            $process.WaitForExit(5000) | Out-Null
        }
        $process.Dispose()
    }
}

function Get-ImageProcesses([string]$Executable) {
    $name = [IO.Path]::GetFileNameWithoutExtension($Executable)
    foreach ($candidate in [Diagnostics.Process]::GetProcessesByName($name)) {
        try {
            if ([IO.Path]::GetFullPath($candidate.MainModule.FileName) -ieq $Executable) {
                Write-Output $candidate
            } else { $candidate.Dispose() }
        } catch { $candidate.Dispose() }
    }
}

function Update-OwnedImageProcesses([string]$Executable, [Collections.Generic.HashSet[int]]$Existing,
                                   [DateTime]$StartedUtc, [hashtable]$Owned) {
    foreach ($candidate in @(Get-ImageProcesses $Executable)) {
        try {
            if (-not $Existing.Contains($candidate.Id) -and
                $candidate.StartTime.ToUniversalTime() -ge $StartedUtc -and
                -not $Owned.ContainsKey($candidate.Id)) {
                # jpackage can fork a second copy of its exact EXE to host the JVM.
                # Existing matching processes are excluded before launch.
                $Owned[$candidate.Id] = $candidate
            } else { $candidate.Dispose() }
        } catch { $candidate.Dispose() }
    }
}

function Test-NativeLauncher([string]$Executable, [string]$Runtime) {
    $existing = New-Object 'System.Collections.Generic.HashSet[int]'
    foreach ($candidate in @(Get-ImageProcesses $Executable)) {
        $null = $existing.Add($candidate.Id)
        $candidate.Dispose()
    }
    $owned = @{}
    $application = New-Object Diagnostics.Process
    $application.StartInfo = New-IsolatedStartInfo $Executable @()
    $started = $false
    $startedUtc = [DateTime]::UtcNow
    $windowProcess = $null
    $jvmPath = $null
    $nativeFailure = $null
    try {
        if (-not $application.Start()) { throw 'The native game launcher could not start.' }
        $started = $true
        $launcherPid = $application.Id
        $owned[$launcherPid] = $application
        $startup = [Diagnostics.Stopwatch]::StartNew()
        while ($startup.Elapsed.TotalSeconds -lt 8) {
            Update-OwnedImageProcesses $Executable $existing $startedUtc $owned
            foreach ($candidate in @($owned.Values)) {
                $candidate.Refresh()
                if ($candidate.HasExited -or $candidate.MainWindowHandle -eq [IntPtr]::Zero) { continue }
                foreach ($loadedModule in $candidate.Modules) {
                    if ($loadedModule.ModuleName -ieq 'jvm.dll') {
                        $jvmPath = $loadedModule.FileName
                        $windowProcess = $candidate
                        break
                    }
                }
                if ($jvmPath) { break }
            }
            if ($jvmPath) { break }
            Start-Sleep -Milliseconds 200
        }
        if (-not $jvmPath) {
            throw 'The native game did not show a window with a loaded JVM within eight seconds.'
        }
        $boundary = $Runtime.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
        $loadedJvm = [IO.Path]::GetFullPath($jvmPath)
        if (-not $loadedJvm.StartsWith($boundary, [StringComparison]::OrdinalIgnoreCase)) {
            throw "The game loaded an external JVM instead of its bundled runtime: $loadedJvm"
        }
        if ($windowProcess.MainWindowTitle -notlike 'The Sunken Seal*') {
            throw ('The launcher did not reach the expected game window: ' + $windowProcess.MainWindowTitle)
        }
        Write-Output ("Native launcher PID $launcherPid, game PID " + $windowProcess.Id + ' loaded bundled JVM: ' + $loadedJvm)
    } catch {
        $nativeFailure = $_
    } finally {
        if ($started) {
            # Discover any last fork before cleanup. Path + new PID + start time must all match.
            Update-OwnedImageProcesses $Executable $existing $startedUtc $owned
            # Close the actual game window first. Its waiting launcher should then exit itself.
            $cleanup = @($owned.Values | Sort-Object @{
                Expression = {
                    if ($null -ne $windowProcess -and $_.Id -eq $windowProcess.Id) { 0 }
                    elseif ($_.Id -eq $launcherPid) { 2 }
                    else { 1 }
                }
            })
            foreach ($candidate in $cleanup) {
                try {
                    $candidate.Refresh()
                    if (-not $candidate.HasExited) {
                        if ($candidate.MainWindowHandle -ne [IntPtr]::Zero) {
                            $null = $candidate.CloseMainWindow()
                        }
                        if (-not $candidate.WaitForExit(5000)) {
                            $candidate.Kill()
                            $candidate.WaitForExit(5000) | Out-Null
                            if ($null -eq $nativeFailure) {
                                $nativeFailure = 'The packaged game did not close normally; only its own process was stopped.'
                            }
                        }
                    }
                } catch {
                    if ($null -eq $nativeFailure) { $nativeFailure = $_ }
                } finally { $candidate.Dispose() }
            }
        } else { $application.Dispose() }
    }
    if ($null -ne $nativeFailure) { throw $nativeFailure }
}

$appImage = Get-ExistingPath $AppImagePath 'app image folder' 'Container'
$launcher = Get-ExistingPath (Join-Path $appImage 'The Sunken Seal.exe') 'native game launcher' 'Leaf'
$jar = Get-ExistingPath (Join-Path $appImage 'app/game.jar') 'packaged game JAR' 'Leaf'
$runtimeRoot = Get-ExistingPath (Join-Path $appImage 'runtime') 'bundled runtime folder' 'Container'
$runtimeJava = Get-ExistingPath (Join-Path $runtimeRoot 'bin/java.exe') 'bundled Java test launcher' 'Leaf'
$null = Get-ExistingPath (Join-Path $appImage 'app/The Sunken Seal.cfg') 'native launcher configuration' 'Leaf'
$null = Get-ExistingPath (Join-Path $runtimeRoot 'release') 'runtime version metadata' 'Leaf'
$null = Get-ExistingPath (Join-Path $runtimeRoot 'legal/java.base') 'runtime license notices' 'Container'
$classes = Get-ExistingPath $TestClassesPath 'compiled test classes' 'Container'
$fixtures = Get-ExistingPath $TestResourcesPath 'test resources' 'Container'
$null = Get-ExistingPath (Join-Path $classes 'tests/GameTests.class') 'compiled regression test entry point' 'Leaf'

$settings = Invoke-BundledJava @('-XshowSettings:properties', '-version') 'Bundled runtime identity'
$identity = $settings.Output + $settings.ErrorOutput
$vendorMatch = [regex]::Match($identity, '(?m)^\s*java\.vendor\s*=\s*(.+?)\s*$')
$runtimeMatch = [regex]::Match($identity, '(?m)^\s*java\.runtime\.name\s*=\s*(.+?)\s*$')
if (-not $vendorMatch.Success -or $vendorMatch.Groups[1].Value -notmatch 'Eclipse Adoptium' -or
    $identity -notmatch 'Temurin') {
    throw 'The packaged runtime is not the expected Eclipse Temurin distribution.'
}
Write-Output ('Bundled runtime: ' + $vendorMatch.Groups[1].Value + ' / ' + $runtimeMatch.Groups[1].Value)

$modulesResult = Invoke-BundledJava @('--list-modules') 'Bundled runtime modules'
$modules = @($modulesResult.Output -split '\r?\n' | ForEach-Object { ($_ -split '@')[0] })
foreach ($module in @('java.base', 'java.desktop', 'java.xml', 'java.prefs', 'java.datatransfer')) {
    if ($modules -notcontains $module) { throw "The runtime is missing $module." }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    foreach ($entry in @('main/Main.class', 'levels/catalog.txt', 'levels/forest.properties',
                        'Player/boy_down_1.png', 'Monster/skeletonlord_phase2_down_1.png',
                        'sounds/BlueBoyAdventure.wav')) {
        if ($null -eq $archive.GetEntry($entry)) { throw "The packaged JAR is missing $entry." }
    }
    $manifestEntry = $archive.GetEntry('META-INF/MANIFEST.MF')
    if ($null -eq $manifestEntry) { throw 'The packaged JAR has no manifest.' }
    $reader = New-Object IO.StreamReader($manifestEntry.Open())
    try { $manifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($manifest -notmatch '(?m)^Main-Class:\s*main\.Main\s*$') {
        throw 'The packaged JAR does not declare the game entry point.'
    }
} finally { $archive.Dispose() }

# Put the packaged JAR ahead of the external test fixtures; source/resources are not on the classpath.
$classpath = $jar + ';' + $classes + ';' + $fixtures
$gameTests = Invoke-BundledJava @('-Djava.awt.headless=true', '-cp', $classpath, 'tests.GameTests') 'Packaged gameplay tests'
Write-Output $gameTests.Output.Trim()
if ($NativeWindows) {
    foreach ($test in @('tests.NativeSmokeTest', 'tests.NativeWindowTest')) {
        $result = Invoke-BundledJava @('-cp', $classpath, $test) $test
        Write-Output $result.Output.Trim()
    }
}

# This checks the actual native EXE, including jpackage's launcher/child process model.
Test-NativeLauncher -Executable $launcher -Runtime $runtimeRoot
Write-Output 'Windows package checks passed without system Java or JAVA_HOME.'
