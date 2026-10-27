package com.gamemodejip.fairypathfinder.location;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;

/**
 * Keeps track of which SkyBlock island the player is currently on.
 * The island id matches the keys in FairySoulData, e.g. "hub" or "mining_3".
 */
public final class IslandDetector {
	private static String currentIsland = null;

	private IslandDetector() {
	}

	/**
	 * Starts listening to Hypixel's location packet. Hypixel sends it every time you join
	 * a server, including when you warp to another island. Leaving the server resets the
	 * island to null, so we never point at souls from an island you're no longer on.
	 * Call once when the mod starts.
	 */
	public static void register() {
		HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class);
		HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class, packet ->
				currentIsland = packet.getMode().orElse(null));

		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> currentIsland = null);
	}

	/** The island the player is on, or null when unknown (not on SkyBlock, or not detected yet). */
	public static String getCurrentIsland() {
		return currentIsland;
	}
}
