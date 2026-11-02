# Development environment

Last updated: 2026-11-02.

## Two machines

| | Cloud build machine | Owner's PC (`robbs-strix`, Windows) |
|---|---|---|
| Builds | `engine`, `preview` (with `-Pveyrath.skipMod=true`) | Everything, including `mod-fabric` |
| Runs | Engine tests, preview renders, benchmarks, audits, installer script against a mock | Minecraft, data generation, installer for real, every in-game test |
| Network | Fabric, Mojang and Modrinth servers are blocked by policy | Normal internet |

The reason for the split is in TD-014. Anything that claims to work in game must have been run
on the PC.

## Requirements (PC)

- **Windows 10 or 11** with the official Minecraft Launcher and a Java Edition account.
- **JDK 25**: Microsoft Build of OpenJDK 25 or Eclipse Temurin 25. Gradle's toolchain resolver
  can download one automatically if none is installed.
- **Git** (Git for Windows).
- **IntelliJ IDEA** (Community is enough). The *Minecraft Development* plugin is optional.
- **PowerShell 5.1** (built into Windows) or PowerShell 7, for the installer.

No global Gradle install is needed. The wrapper pins **Gradle 9.6.0**.

## Getting the code

```powershell
git clone https://github.com/robb-devo/veyrath.git
cd veyrath
```

Open the folder in IntelliJ as a Gradle project. The first sync downloads Minecraft 26.3,
Fabric Loader, Fabric API and the Distant Horizons jar, which takes a few minutes.

## Everyday commands

| Task | Command |
|---|---|
| Engine tests | `./gradlew :engine:test` |
| Atlas maps for a seed | `./gradlew :preview:run --args="atlas --seed 1"` (output in `preview-out/`) |
| Perspective renders | `./gradlew :preview:run --args="view --seed 1"` |
| Engine benchmark | `./gradlew :preview:run --args="bench --seed 1"` |
| World-quality audit | `./gradlew :preview:run --args="audit --seed 1"` |
| Build the mod jar | `./gradlew :mod-fabric:build` (jar in `mod-fabric/build/libs/`) |
| Regenerate biome JSON | `./gradlew :mod-fabric:runDatagen`, then commit `mod-fabric/src/main/generated/` |
| Development client | `./gradlew :mod-fabric:runClient` |
| Development client with Sodium, Iris and DH | `./gradlew :mod-fabric:runClient -Pveyrath.devMods=true` |
| Build without the DH integration | add `-Pveyrath.dh=false` |
| Cloud or offline build (no mod) | add `-Pveyrath.skipMod=true` |

On Windows use `gradlew.bat` or `.\gradlew` in PowerShell.

## Isolation from the owner's Minecraft

- Development runs use `mod-fabric/run/client` and `mod-fabric/run/server`, inside the
  repository and git-ignored. They never touch `%APPDATA%\.minecraft`.
- The player profile uses its own game directory, `%APPDATA%\.minecraft-veyrath`, created by
  the installer (TD-011).
- The installer changes only one entry in `launcher_profiles.json`, the one with id `veyrath`,
  after backing the file up. Other profiles, worlds, resource packs and settings are untouched.

## Hosts the build and installer need

| Host | Used for |
|---|---|
| `maven.fabricmc.net` | Loom, Fabric Loader, Fabric API, Fabric's Minecraft metadata |
| `meta.fabricmc.net` | Installer: the Fabric loader profile JSON |
| `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net` | Minecraft jars, libraries and assets (Loom and the launcher) |
| `api.modrinth.com`, `cdn.modrinth.com` | Distant Horizons compile dependency (Modrinth Maven) and installer downloads |
| `plugins.gradle.org`, `repo.maven.apache.org` | Gradle plugins, JUnit |
| `api.foojay.io` | Optional automatic JDK download |

## Troubleshooting

- **`UnsupportedClassVersionError ... class file version 69`.** Gradle or the preview tool is
  running on Java 21 or older. Put JDK 25 on `PATH` or set `JAVA_HOME`.
- **Maven Central answers 429 (rate limited).** Retry, or use `--max-workers=2`.
- **DH dependency does not resolve.** Switch to the API artifact shown in
  `mod-fabric/build.gradle.kts`, or build with `-Pveyrath.dh=false` and report it.
