# Roadmap

## Done
- Project setup: Fabric 26.1.2, Java 25, client-only mod
- `FairySoulData`: loads 271 souls on 16 islands from the bundled NEU data
- Hypixel Mod API added as a dependency

## In progress: `IslandDetector`
1. ~~Empty class with `getCurrentIsland()`~~
2. Read the island id from the Hypixel Mod API location packet
3. Reset to `null` when leaving or switching servers
4. Temporary log line to test in-game (and verify the island ids in the data docs)
5. Remove the log line

## Planned
- **`FoundSoulTracker`**: detect collected souls from chat, save per profile
- **`PathFinder`**: A* over walkable blocks, works through caves
- **`PathRenderer`**: path line + marker on the target soul
- **Navigation commands**, `/shnavigate`-style: navigate to the nearest missing soul, stop,
  skip to the next one
- **Config menu**: in-game settings screen, e.g.
  - enable/disable the mod
  - path colour and thickness
  - show path through walls or not
  - automatically move on to the next soul after collecting one
  - maximum search distance (performance)
