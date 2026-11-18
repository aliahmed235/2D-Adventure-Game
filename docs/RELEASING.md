# Windows releases

The player download is a portable Windows x64 ZIP. Its native launcher loads the runtime beside it, so players do not need Java, .NET, an installer, or administrator access.

```text
True Adventure/
  True Adventure.exe
  READ ME FIRST.txt
  RUNTIME-NOTICES.txt
  build-info.json
  app/
    game.jar
    True Adventure.cfg
  runtime/
    bin/
    lib/
    legal/
```

The existing `build.ps1` remains the quick source-build workflow. The portable release does not copy the old `dist/java` JDK or the legacy C# launcher. It uses `jlink` to retain `java.desktop` and its required modules, then `jpackage --type app-image` to make the native Windows launcher. [The Java packaging guide](https://docs.oracle.com/en/java/javase/17/jpackage/packaging-overview.html) describes this self-contained application layout.

## Build and check

From the project folder on 64-bit Windows:

```powershell
.\package-windows.ps1 -Version 2.0.1 -Test
```

The script downloads the pinned Eclipse Temurin toolchain and matching source archive, verifies both SHA-256 checksums, and caches them under `build/toolchains`. Pin details are in `tools/temurin-windows-x64.json`. It then:

1. Compiles the current source and runs the headless game tests.
2. Builds a small runtime and native application image.
3. Adds player instructions, runtime notices, licenses, and build information.
4. Runs the game tests using the packaged JAR and bundled runtime.
5. Checks native window startup and fullscreen entry/restoration.
6. Launches the actual packaged EXE with `JAVA_HOME` unset and Java absent from its `PATH`, verifies that its loaded `jvm.dll` belongs to the package, then closes that test process.
7. Writes the Windows ZIP, optional standalone JAR, matching runtime source, and `SHA256SUMS.txt` under `build/releases/2.0.1`.

The window checks briefly display the test game. They close only their own processes. For another test of an existing app image:

```powershell
.\tools\test-windows-package.ps1 -AppImagePath '.\build\packaging\2.0.1\image\True Adventure' -NativeWindows
```

Use `-SkipBuild` only when `build/true-adventure.jar` already matches the current source. Combining it with `-Test` also requires compiled test classes from `build.ps1 -Test`.

## Publish

Create a new versioned release and retain the earlier release. Commit and push the release recipe/documentation first so the release tag points at reviewable source. Rebuild with `-SkipBuild` after committing to record the final source commit in `build-info.json`.

Upload these files:

- `True-Adventure-2.0.1-Windows-x64.zip` — recommended player download; no Java installation.
- `True-Adventure-2.0.1.jar` — optional Java 17+ download.
- `SHA256SUMS.txt` — checksums for the ZIP, JAR, and matching runtime source archive.

The package script still writes a local copy of `OpenJDK17U-jdk-sources_17.0.21_9.tar.gz` and includes its checksum. Version 2.0.1 uses the same verified runtime as 2.0.0, so reuse the existing [matching runtime source asset](https://github.com/aliahmed235/2D-Adventure-Game/releases/download/v2.0.0/OpenJDK17U-jdk-sources_17.0.21_9.tar.gz) through explicit links in the release notes and packaged runtime notices. This identical source archive does not need another upload.

Use the release description in `docs/releases/v2.0.1.md` and mark the new stable version as latest. The runtime notices remain inside the player ZIP; the source archive is a separate download so it does not increase the player's package size. Temurin's licenses are included in its distribution. [Adoptium's FAQ](https://adoptium.net/docs/faq) describes its licensing and use.

For a future runtime update, update the pinned version, URLs, and hashes together, retain the matching source and notices, then rerun the package checks. macOS and Linux packages need builds on their respective operating systems.
