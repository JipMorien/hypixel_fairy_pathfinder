# Architecture

Fairy Pathfinder is a **client-side, display-only** Fabric mod. It never moves, clicks, or sends
anything for the player; it only reads game state and draws on screen. That keeps it within
Hypixel's rules for mods (same category as waypoint and path mods like SkyHanni and Skyblocker).

## Data flow

```
fairy_souls.json ──► FairySoulData ──┐
                                     ├──► target soul ──► PathFinder ──► PathRenderer ──► screen
Hypixel Mod API ──► IslandDetector ──┤
                                     │
chat messages ──► FoundSoulTracker ──┘          SoulCommands / ConfigScreen control all of this
```

1. **FairySoulData** knows where every soul is, per island.
2. **IslandDetector** knows which island you're on.
3. **FoundSoulTracker** knows which souls you've already collected.
4. Combining those gives the souls still missing on your island; the nearest one becomes the target.
5. **PathFinder** finds a walkable route from you to the target.
6. **PathRenderer** draws that route in the world.

## Classes

All code lives in `src/client/java/com/gamemodejip/fairypathfinder/`.

| Class | Package | Status | Responsibility |
|---|---|---|---|
| `FairyPathfinderClient` | (root) | Done | Entry point. Starts every other part when the game loads. |
| `FairySoulData` | `data` | Done | Loads the bundled soul coordinates and answers "which souls are on island X". |
| `IslandDetector` | `location` | Done | Listens to the Hypixel Mod API location packet and stores the current island id; resets to `null` on disconnect. |
| `FoundSoulTracker` | `tracking` | Planned | Reads "SOUL! You found a Fairy Soul!" / "You have already found that Fairy Soul!" from chat and saves found souls per SkyBlock profile. |
| `PathFinder` | `navigation` | Planned | A* search over walkable blocks between the player and the target soul, including caves. |
| `PathRenderer` | `render` | Planned | Draws the path line and a marker on the target soul. |
| `SoulCommands` | `command` | Planned | Client commands to start/stop navigation, `/shnavigate`-style. |
| `ConfigScreen` + `ModConfig` | `config` | Planned | In-game settings menu and the saved settings file. |

## Design decisions

- **Island detection uses the Hypixel Mod API**, not the scoreboard. The API's location packet
  gives the island id directly (`getMode()`, e.g. `"hub"`), which matches the keys in
  `fairy_souls.json`. Scoreboard text would need translating and breaks when Hypixel changes it.
- **Soul data is bundled**, not downloaded at runtime. Simpler and works offline; the trade-off
  is that the file must be refreshed by hand when Hypixel adds souls (see
  [soul-data-format.md](soul-data-format.md)).
- **Static classes for now.** Each part has a single global state (one player, one island),
  so plain static classes keep the code short. Can be refactored if it grows.

## Open design question: pathfinding approach

SkyHanni's `/shnavigate` uses **pre-built island graphs**: hand-made networks of points and
connections per island. This mod has two options:

| | A* over the live world (planned) | Pre-built graphs (like SkyHanni) |
|---|---|---|
| Works in caves | Yes, uses the actual blocks | Only where the graph has nodes |
| Setup work | None per island | A graph per island, maintained by hand |
| Range | Limited to loaded chunks (render distance) | Whole island |
| Cost | Pathfinding runs on the client, must be limited | Cheap lookup |

Plan: start with A* over the live world, and re-path as the player moves so distant souls
still work once their chunks load. Revisit if range becomes a problem.
