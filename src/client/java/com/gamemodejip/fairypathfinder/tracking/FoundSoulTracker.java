package com.gamemodejip.fairypathfinder.tracking;

import com.gamemodejip.fairypathfinder.FairyPathfinderClient;
import com.gamemodejip.fairypathfinder.data.FairySoulData;
import com.gamemodejip.fairypathfinder.location.IslandDetector;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remembers which Fairy Souls the player has already collected, separately per SkyBlock profile.
 * Souls are identified by their position, the same BlockPos values as in FairySoulData.
 */
public final class FoundSoulTracker {
	private static final String FOUND_MESSAGE = "SOUL! You found a Fairy Soul!";
	private static final String ALREADY_FOUND_MESSAGE = "You have already found that Fairy Soul!";
	/** How far (in blocks) the clicked soul can be from the player. Reach is ~4.5, plus some margin. */
	private static final double MAX_SOUL_DISTANCE = 8;

	/** Hypixel sends "Profile ID: <uuid>" in chat every time you join a SkyBlock server. */
	private static final Pattern PROFILE_ID_PATTERN = Pattern.compile("Profile ID: ([0-9a-f-]{36})");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path SAVE_FILE = FabricLoader.getInstance().getConfigDir()
			.resolve("fairypathfinder").resolve("found_souls.json");

	private static final Map<String, Set<BlockPos>> FOUND_PER_PROFILE = new HashMap<>();
	private static String currentProfileId = null;

	private FoundSoulTracker() {
	}

	/** Loads the save file, then starts listening to chat. Call once when the mod starts. */
	public static void register() {
		load();

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) {
				return; // action bar text, not chat
			}
			String text = ChatFormatting.stripFormatting(message.getString());
			Matcher profileMatcher = PROFILE_ID_PATTERN.matcher(text);
			if (profileMatcher.find()) {
				currentProfileId = profileMatcher.group(1);
			} else if (text.contains(FOUND_MESSAGE) || text.contains(ALREADY_FOUND_MESSAGE)) {
				markNearestSoul();
			}
		});
	}

	/**
	 * Both messages appear right after clicking a soul, so the clicked soul is the closest one
	 * to the player. Marks it as found, unless nothing is within MAX_SOUL_DISTANCE.
	 */
	private static void markNearestSoul() {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		BlockPos playerPos = player.blockPosition();

		BlockPos nearest = null;
		double nearestDistance = MAX_SOUL_DISTANCE * MAX_SOUL_DISTANCE;
		for (BlockPos soul : FairySoulData.getSouls(IslandDetector.getCurrentIsland())) {
			double distance = soul.distSqr(playerPos);
			if (distance <= nearestDistance) {
				nearest = soul;
				nearestDistance = distance;
			}
		}

		if (nearest != null) {
			markFound(nearest);
		}
	}

	/** Records a soul as collected on the current profile. Ignored until the profile is known. */
	public static void markFound(BlockPos soul) {
		if (currentProfileId == null) {
			return;
		}
		boolean isNew = FOUND_PER_PROFILE.computeIfAbsent(currentProfileId, id -> new HashSet<>()).add(soul);
		if (isNew) {
			save();
		}
	}

	/**
	 * Marks every soul on an island as found, e.g. when the Quest Log shows the island is complete.
	 * Saves once at the end instead of once per soul.
	 */
	public static void markAllFound(String islandId) {
		if (currentProfileId == null) {
			return;
		}
		Set<BlockPos> found = FOUND_PER_PROFILE.computeIfAbsent(currentProfileId, id -> new HashSet<>());
		if (found.addAll(FairySoulData.getSouls(islandId))) {
			save();
		}
	}

	public static boolean isFound(BlockPos soul) {
		return FOUND_PER_PROFILE.getOrDefault(currentProfileId, Set.of()).contains(soul);
	}

	/** The souls on an island that haven't been collected yet. This is what navigation will target. */
	public static List<BlockPos> getMissingSouls(String islandId) {
		return FairySoulData.getSouls(islandId).stream()
				.filter(soul -> !isFound(soul))
				.toList();
	}

	/**
	 * Reads config/fairypathfinder/found_souls.json. Format, per profile id, the same "x,y,z"
	 * strings as the soul data: { "profile-uuid": ["-233,86,84", ...] }
	 */
	private static void load() {
		if (!Files.exists(SAVE_FILE)) {
			return; // first run, nothing saved yet
		}
		try (Reader reader = Files.newBufferedReader(SAVE_FILE)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			for (Map.Entry<String, JsonElement> profile : root.entrySet()) {
				Set<BlockPos> souls = new HashSet<>();
				for (JsonElement element : profile.getValue().getAsJsonArray()) {
					String[] parts = element.getAsString().split(",");
					souls.add(new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
				}
				FOUND_PER_PROFILE.put(profile.getKey(), souls);
			}
		} catch (Exception e) {
			FairyPathfinderClient.LOGGER.error("Failed to load found souls from {}", SAVE_FILE, e);
		}
	}

	/** Writes every profile's found souls to the save file. Called after each new soul. */
	private static void save() {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, Set<BlockPos>> profile : FOUND_PER_PROFILE.entrySet()) {
			JsonArray souls = new JsonArray();
			for (BlockPos soul : profile.getValue()) {
				souls.add(soul.getX() + "," + soul.getY() + "," + soul.getZ());
			}
			root.add(profile.getKey(), souls);
		}
		try {
			Files.createDirectories(SAVE_FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(SAVE_FILE)) {
				GSON.toJson(root, writer);
			}
		} catch (Exception e) {
			FairyPathfinderClient.LOGGER.error("Failed to save found souls to {}", SAVE_FILE, e);
		}
	}
}
