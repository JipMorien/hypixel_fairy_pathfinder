package com.gamemodejip.fairypathfinder.location;

/**
 * Keeps track of which SkyBlock island the player is currently on.
 * The island id matches the keys in FairySoulData, e.g. "hub" or "mining_3".
 */
public final class IslandDetector {
	private static String currentIsland = null;

	private IslandDetector() {
	}

	/** The island the player is on, or null when unknown (not on SkyBlock, or not detected yet). */
	public static String getCurrentIsland() {
		return currentIsland;
	}
}
