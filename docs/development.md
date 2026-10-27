# Development

## Requirements
- **Java 25** (Eclipse Temurin). Minecraft 26.1 requires it.
- **IntelliJ IDEA 2025.3+**, with the *Minecraft Development* plugin (optional, helps with Fabric).

## Versions

| Thing | Version | Where it's set |
|---|---|---|
| Minecraft | 26.1.2 | `gradle.properties` |
| Fabric Loader | 0.19.3 (matches the SkyBlock Enhanced pack) | `gradle.properties`, `fabric.mod.json` |
| Fabric API | 0.155.3+26.1.2 | `gradle.properties` |
| Fabric Loom | 1.19-SNAPSHOT | `gradle.properties` |
| Hypixel Mod API | 1.0.2 (compile) / 1.0.2+build.1+mc26.1 (runtime) | `gradle.properties` |

Minecraft 26.1 is unobfuscated: there are no mappings, and code uses Mojang's own class names
(e.g. `net.minecraft.core.BlockPos`).

## Setup
1. Open the repo folder in IntelliJ.
2. *Settings → Build Tools → Gradle → Gradle JVM*: temurin-25.
3. *File → Project Structure → Project → SDK*: temurin-25.
4. Reload the Gradle project.

## Running
- **Minecraft Client** run config starts a test game with the mod.
- The test game uses an offline account, so it **can't join Hypixel**. Use it to check the mod
  loads (log line `Fairy Pathfinder loaded`) and for anything that doesn't need the server.

## Testing on Hypixel
1. Run the Gradle task `build`.
2. Copy `build/libs/fairy-pathfinder-<version>.jar` (not the `-sources` jar) into the
   SkyBlock Enhanced instance's `mods` folder.
3. Start the pack normally and join Hypixel.
4. Check the game log (`logs/latest.log` in the instance folder) for `fairypathfinder` lines.

The Hypixel Mod API mod must be installed in the instance; SkyBlock Enhanced already includes
it for SkyHanni and Skyblocker.

## Working style
Small steps: one function or one small change per commit, tested before the next.
