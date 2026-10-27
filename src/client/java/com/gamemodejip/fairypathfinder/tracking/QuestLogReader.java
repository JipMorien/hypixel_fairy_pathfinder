package com.gamemodejip.fairypathfinder.tracking;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the "Fairy Souls Guide" menu (SkyBlock Menu → Quest Log → Fairy Souls) when the player
 * opens it. Each island is an item named after the island, with a "Fairy Souls: X/Y" lore line.
 * When an island shows X == Y, every soul there gets marked as found.
 * Only reads what's on screen; never clicks anything.
 */
public final class QuestLogReader {
	private static final String GUIDE_TITLE = "Fairy Souls Guide";
	private static final Pattern COUNT_PATTERN = Pattern.compile("Fairy Souls: (\\d+)/(\\d+)");

	/**
	 * Item name in the guide → island id in FairySoulData. Totals checked in-game on 27 Oct 2026.
	 * "Miscellaneous" and "The Rift" are left out: those souls have no fixed location in the data.
	 */
	private static final Map<String, String> ISLAND_IDS = Map.ofEntries(
			Map.entry("Hub", "hub"),
			Map.entry("Crimson Isle", "crimson_isle"),
			Map.entry("Deep Caverns", "mining_2"),
			Map.entry("The Farming Islands", "farming_1"),
			Map.entry("Spider's Den", "combat_1"),
			Map.entry("Dwarven Mines", "mining_3"),
			Map.entry("Gold Mine", "mining_1"),
			Map.entry("The End", "combat_3"),
			Map.entry("The Park", "foraging_1"),
			Map.entry("Moonglade Marsh", "foraging_2"),
			Map.entry("Torrhus Canyon", "foraging_3"),
			Map.entry("Dungeon Hub", "dungeon_hub"),
			Map.entry("Lotus Atoll", "lotus_atoll"),
			Map.entry("Jerry's Workshop", "winter"),
			Map.entry("Backwater Bayou", "fishing_1"),
			Map.entry("Safari", "safari"));

	/** How many island items we read last time, so we only re-read when the menu changes. */
	private static int lastReadIslandCount = -1;

	private QuestLogReader() {
	}

	/** Watches for the Fairy Souls Guide to open. Call once when the mod starts. */
	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
				return;
			}
			if (!screen.getTitle().getString().contains(GUIDE_TITLE)) {
				return;
			}
			// Hypixel fills the slots a moment after the screen opens, so check every tick
			lastReadIslandCount = -1;
			ScreenEvents.afterTick(screen).register(tickedScreen -> readIslands(containerScreen));
		});
	}

	/** Goes over the island items and marks every soul on completed islands as found. */
	private static void readIslands(AbstractContainerScreen<?> screen) {
		var islandSlots = screen.getMenu().slots.stream()
				.filter(slot -> !(slot.container instanceof Inventory)) // skip the player's own inventory
				.filter(slot -> ISLAND_IDS.containsKey(slot.getItem().getHoverName().getString()))
				.toList();
		if (islandSlots.isEmpty() || islandSlots.size() == lastReadIslandCount) {
			return;
		}
		lastReadIslandCount = islandSlots.size();

		for (Slot slot : islandSlots) {
			ItemStack stack = slot.getItem();
			String islandId = ISLAND_IDS.get(stack.getHoverName().getString());
			int[] count = readCount(stack);
			if (count == null) {
				continue;
			}
			if (count[0] == count[1]) {
				FoundSoulTracker.markAllFound(islandId);
			}
		}
	}

	/** Finds the "Fairy Souls: X/Y" lore line. Returns {found, total}, or null if it isn't there. */
	private static int[] readCount(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return null;
		}
		for (Component line : lore.lines()) {
			Matcher matcher = COUNT_PATTERN.matcher(line.getString());
			if (matcher.find()) {
				return new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
			}
		}
		return null;
	}
}
