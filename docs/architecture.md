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
chat + Quest Log ──► FoundSoulTracker ┘          SoulCommands / ConfigScreen control all of this
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
| `FoundSoulTracker` | `tracking` | Done | Reads "SOUL! You found a Fairy Soul!" / "You have already found that Fairy Soul!" from chat, marks the nearest soul (within 8 blocks) as found, per SkyBlock profile (from the "Profile ID:" join message). Saved to `config/fairypathfinder/found_souls.json`. |
| `QuestLogReader` | `tracking` | Done | Reads the "Fairy Souls Guide" menu (Quest Log) when the player opens it. Translates item names ("Spider's Den") to island ids (`combat_1`) and marks every soul on islands showing X/X as found. |
| `SoulNavigator` | `navigation` | Done | Picks the target (nearest missing soul), runs `PathFinder` searches on a background thread, and trims the finished route as the player walks it. Searches again only when the soul changes, the player leaves the route (>4 blocks), or nears the end of a partial route. |
| `PathFinder` | `navigation` | Done | A* through open space (2 blocks free, 26 directions, no floor needed). Ends where the soul is visible within 4 blocks (always within 2.5). Returns a partial route if the soul can't be reached within 80k positions. |
| `PathRenderer` | `render` | Done | Draws the route with Gizmos: first 8 steps bright and thick, a box on the soul, and a box at the end of a partial route (no line through walls). |
| `DirectionHud` | `render` | Done | Arrow under the crosshair pointing along the route, plus distance and height difference, e.g. `↗ 34m ▲6`; `…` when the route is partial. |
| `SoulCommands` | `command` | Planned | Client commands to start/stop navigation, `/shnavigate`-style. |
| `ConfigScreen` + `ModConfig` | `config` | Planned | In-game settings menu and the saved settings file. |

## Design decisions

- **Island detection uses the Hypixel Mod API**, not the scoreboard. The API's location packet
  gives the island id directly (`getMode()`, e.g. `"hub"`), which matches the keys in
  `fairy_souls.json`. Scoreboard text would need translating and breaks when Hypixel changes it.
- **Soul data is bundled**, not downloaded at runtime. Simpler and works offline; the trade-off
  is that the file must be refreshed by hand when Hypixel adds souls (see
  [soul-data-format.md](soul-data-format.md)).
- **Found souls can't be fully auto-detected.** Hypixel doesn't expose *which* souls you
  found, only per-island counts (Quest Log). The mod combines: souls you click (exact) +
  fully completed islands from the Quest Log (all souls there). The mod never clicks souls
  itself; that would be automation.
- **Drawing uses Minecraft's Gizmos** (`net.minecraft.gizmos.Gizmos`), the game's own debug
  drawing. Gizmos must be created while Minecraft is collecting them or the game crashes;
  the client tick is such a moment, so `PathRenderer` draws from `END_CLIENT_TICK`
  (checked in the decompiled 26.1.2 `Minecraft.runTick`). Drawn per tick (20/s), so the line
  can trail slightly behind a moving player.
- **Static classes for now.** Each part has a single global state (one player, one island),
  so plain static classes keep the code short. Can be refactored if it grows.

## Pathfinding design (decided 27 Oct 2026)

- **A* over the live world**, not pre-built island graphs (SkyHanni-style): works in caves
  without hand-made maps.
- **Free space, not walking.** First version only allowed walking (8 directions, 1-block jumps,
  drops up to 3, ladders). In SkyBlock that missed too many routes: drops of any height are fine,
  and with AOTE and fall-damage cancelling a player gets almost anywhere there's room. Rule now:
  "2 blocks of free space", all 26 directions. Going up costs a bit extra (`UP_PENALTY`).
- **Weighted A*** (`HEURISTIC_WEIGHT = 2.5`): much fewer positions examined, route can be a bit
  longer than the shortest.
- **Background thread.** On the game thread a far search took 4-17 s at a few ms per tick.
  `SoulNavigator` now runs searches on one low-priority daemon thread. Only reads the world;
  reading from another thread isn't officially supported, so failures are caught and retried.
- **Route trimming.** A finished route is kept and cut down to the part ahead of the player each
  tick, instead of searching again every few blocks.
- **fastutil long-keyed collections** in the search (positions as `BlockPos.asLong()`).

## Old design question (kept for reference): pathfinding approach

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
