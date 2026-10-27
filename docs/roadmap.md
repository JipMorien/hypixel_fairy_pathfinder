# Roadmap

## Done
- Project setup: Fabric 26.1.2, Java 25, client-only mod
- `FairySoulData`: loads 271 souls on 16 islands from the bundled NEU data
- Hypixel Mod API added as a dependency
- `IslandDetector`: island id from the Hypixel Mod API, reset on disconnect, tested in-game
- `FoundSoulTracker`: found souls from chat, per profile, saved to JSON, tested in-game
- `QuestLogReader`: completed islands from the Fairy Souls Guide auto-marked as found, tested in-game
- `SoulNavigator` + `PathRenderer`: box on the nearest missing soul and a straight line to it, tested in-game
- `PathFinder`: A* through open space, partial routes, background thread, route trimming, tested in-game
- `DirectionHud`: arrow + distance + height under the crosshair, tested in-game

## Planned
- **Smarter partial islands** (idea): remember the Quest Log count per island (e.g. Hub 78/80);
  once enough souls there are known as found, the rest must be the missing ones
- **Navigation commands**, `/shnavigate`-style: navigate to the nearest missing soul, stop,
  skip to the next one
- **Config menu**: in-game settings screen, e.g.
  - enable/disable the mod
  - path colour and thickness
  - show path through walls or not
  - automatically move on to the next soul after collecting one
  - maximum search distance (performance)

## Status / next session (27 Oct 2026)

Last state: navigation works with the free-space pathfinder on a background thread. Last
things changed before this commit: background thread + route trimming (updates went from
4-17 s per update to an acceptable speed; confirmed in the final in-game test on 27 Oct 2026).

**Still in the code, remove before calling navigation finished:**
- `TEMP (debug)` grey HUD line under the arrow (`SoulNavigator.lastSearchInfo`, drawn in `DirectionHud`)
- `TEMP (debug)` cyan dots on the explored area when a search runs out of paths
  (`SoulNavigator.lastExploredArea`, drawn in `PathRenderer`; `examined()`, `ranOutOfPaths()`,
  `examinedCount()`, `debugInfo()`, `stopReason` in `PathFinder.Search`)

**Next steps, in order:**
1. Tune if routes feel off (`HEURISTIC_WEIGHT`, `MAX_EXPANDED_NODES`,
   `OFF_ROUTE_DISTANCE`, `CONTINUE_NEAR_END_STEPS`).
2. Remove the TEMP debug code.
3. Config menu (`ConfigScreen` + `ModConfig`): on/off, path colour/thickness, debug toggle,
   AOTE settings.
4. AOTE as an explicit move (teleport up to 8 blocks in a straight line, drawn in another colour).
5. Commands (`/shnavigate`-style): navigate to nearest, stop, skip to next soul.
6. Smarter partial islands (Quest Log count, e.g. Hub 78/80).

**Known issues / ideas:**
- Target is the nearest soul in a straight line; can pick one far below or behind walls.
  Idea: prefer souls at about the player's height, or use route length.
- One Dwarven Mines soul seems to be behind a wall the player can't get through.
- Route can cut through the air across big gaps (by design, see architecture.md).

**Working agreement:** small steps, each built and tested; commits per finished piece.
Claude edits existing files directly on the device (device_bash), because re-sending a
previously committed file with device_commit_files delivered an old copy once.
