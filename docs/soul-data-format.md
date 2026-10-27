# Soul data format

File: `src/client/resources/assets/fairypathfinder/data/fairy_souls.json`

Source: `constants/fairy_souls.json` from the
[NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO) (MIT License,
Copyright (c) 2020 Moulberry). Copied on 27 Oct 2026.

## Structure

```json
{
  "//": "comment from the NEU maintainers",
  "Max Souls": 289,
  "hub": ["-233,86,84", "-229,123,84", "..."],
  "mining_3": ["-9,230,-135", "..."]
}
```

- Each **island key** maps to a list of positions as `"x,y,z"` strings.
- The coordinates are the soul itself, not where you stand to grab it.
- Entries that aren't lists (`"//"`, `"Max Souls"`) are skipped by `FairySoulData`.

## Island ids

The keys are Hypixel's island ids, the same value the Hypixel Mod API returns from
`ClientboundLocationPacket.getMode()`.

| Key | Island | Souls |
|---|---|---|
| `hub` | Hub | 80 |
| `crimson_isle` | Crimson Isle | 29 |
| `mining_2` | Deep Caverns | 21 |
| `farming_1` | The Farming Islands | 20 |
| `combat_1` | Spider's Den | 19 |
| `mining_3` | Dwarven Mines | 15 |
| `mining_1` | Gold Mine | 12 |
| `combat_3` | The End | 12 |
| `foraging_1` | The Park | 12 |
| `foraging_2` | Galatea area (to verify in-game) | 12 |
| `foraging_3` | Galatea area (to verify in-game) | 12 |
| `dungeon_hub` | Dungeon Hub | 7 |
| `lotus_atoll` | Lotus Atoll | 6 |
| `winter` | Jerry's Workshop | 5 |
| `fishing_1` | Backwater Bayou (to verify in-game) | 5 |
| `safari` | Critter Safari | 4 |
| **Total** | | **271** |

The other 18 of the 289 souls come from dungeons, fishing and the Garden. They have no fixed
location, so the mod doesn't navigate to them.

Rows marked "to verify" get confirmed once `IslandDetector` logs the island id in-game.

## Updating the data

1. Download the latest `constants/fairy_souls.json` from the NEU repo.
2. Replace the bundled file.
3. Update the copy date above and the table if islands changed.
4. Start the game and check the log line `Loaded N fairy souls on M islands`.
