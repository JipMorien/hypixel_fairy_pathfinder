# Roadmap

## Done
- Project setup: Fabric 26.1.2, Java 25, client-only mod
- `FairySoulData`: loads 271 souls on 16 islands from the bundled NEU data
- Hypixel Mod API added as a dependency
- `IslandDetector`: island id from the Hypixel Mod API, reset on disconnect, tested in-game
- `FoundSoulTracker`: found souls from chat, per profile, saved to JSON, tested in-game
- `QuestLogReader`: completed islands from the Fairy Souls Guide auto-marked as found, tested in-game
- `SoulNavigator` + `PathRenderer`: box on the nearest missing soul and a straight line to it, tested in-game

## Planned
- **Smarter partial islands** (idea): remember the Quest Log count per island (e.g. Hub 78/80);
  once enough souls there are known as found, the rest must be the missing ones
- **`PathFinder`**: A* over walkable blocks, works through caves; `PathRenderer` then draws
  the route instead of a straight line
- **Navigation commands**, `/shnavigate`-style: navigate to the nearest missing soul, stop,
  skip to the next one
- **Config menu**: in-game settings screen, e.g.
  - enable/disable the mod
  - path colour and thickness
  - show path through walls or not
  - automatically move on to the next soul after collecting one
  - maximum search distance (performance)
