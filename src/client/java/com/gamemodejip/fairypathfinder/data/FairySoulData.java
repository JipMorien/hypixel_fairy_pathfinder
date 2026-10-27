package com.gamemodejip.fairypathfinder.data;

import com.gamemodejip.fairypathfinder.FairyPathfinderClient;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds the coordinates of every Fairy Soul, grouped per island.
 *
 * The data comes from the NotEnoughUpdates community repo (MIT licensed), bundled as
 * assets/fairypathfinder/data/fairy_souls.json. Island keys are Hypixel's own island
 * ids (the "mode" value), e.g. "hub", "mining_3" (Dwarven Mines), "combat_1" (Spider's Den).
 */
public final class FairySoulData {
	private static final String RESOURCE_PATH = "/assets/fairypathfinder/data/fairy_souls.json";

	private static final Map<String, List<BlockPos>> SOULS_PER_ISLAND = new HashMap<>();
	private static int totalSouls = 0;

	private FairySoulData() {
	}

	/** Reads the bundled JSON file. Call once when the mod starts. */
	public static void load() {
		SOULS_PER_ISLAND.clear();
		totalSouls = 0;

		try (InputStream stream = FairySoulData.class.getResourceAsStream(RESOURCE_PATH)) {
			if (stream == null) {
				FairyPathfinderClient.LOGGER.error("Fairy soul data not found at {}", RESOURCE_PATH);
				return;
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				parse(JsonParser.parseReader(reader).getAsJsonObject());
			}
		} catch (Exception e) {
			FairyPathfinderClient.LOGGER.error("Failed to load fairy soul data", e);
		}

		FairyPathfinderClient.LOGGER.info("Loaded {} fairy souls on {} islands", totalSouls, SOULS_PER_ISLAND.size());
	}

	/** Turns the JSON into lists of BlockPos. Skips entries that aren't soul lists, like "//" and "Max Souls". */
	private static void parse(JsonObject root) {
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (!entry.getValue().isJsonArray()) {
				continue;
			}

			List<BlockPos> souls = new ArrayList<>();
			for (JsonElement element : entry.getValue().getAsJsonArray()) {
				BlockPos pos = parsePosition(element.getAsString());
				if (pos != null) {
					souls.add(pos);
				}
			}

			SOULS_PER_ISLAND.put(entry.getKey(), Collections.unmodifiableList(souls));
			totalSouls += souls.size();
		}
	}

	/** Converts "x,y,z" into a BlockPos. Returns null (and logs) if the text is malformed. */
	private static BlockPos parsePosition(String text) {
		String[] parts = text.split(",");
		if (parts.length != 3) {
			FairyPathfinderClient.LOGGER.warn("Skipping malformed soul position: {}", text);
			return null;
		}
		try {
			return new BlockPos(
					Integer.parseInt(parts[0].trim()),
					Integer.parseInt(parts[1].trim()),
					Integer.parseInt(parts[2].trim()));
		} catch (NumberFormatException e) {
			FairyPathfinderClient.LOGGER.warn("Skipping malformed soul position: {}", text);
			return null;
		}
	}

	/** All souls on one island, or an empty list if we have no data for it. */
	public static List<BlockPos> getSouls(String islandId) {
		return SOULS_PER_ISLAND.getOrDefault(islandId, List.of());
	}

	/** Every island id we have souls for. */
	public static Set<String> getIslands() {
		return Collections.unmodifiableSet(SOULS_PER_ISLAND.keySet());
	}

	public static int getTotalSouls() {
		return totalSouls;
	}
}
