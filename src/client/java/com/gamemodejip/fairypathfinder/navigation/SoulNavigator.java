package com.gamemodejip.fairypathfinder.navigation;

import com.gamemodejip.fairypathfinder.location.IslandDetector;
import com.gamemodejip.fairypathfinder.tracking.FoundSoulTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Decides which Fairy Soul to navigate to: the nearest one on the current island
 * that hasn't been found yet.
 */
public final class SoulNavigator {
	private SoulNavigator() {
	}

	/** The nearest missing soul on the player's island, or null if there is none (or no player/island). */
	public static BlockPos findNearestMissingSoul() {
		var player = Minecraft.getInstance().player;
		String island = IslandDetector.getCurrentIsland();
		if (player == null || island == null) {
			return null;
		}
		BlockPos playerPos = player.blockPosition();

		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (BlockPos soul : FoundSoulTracker.getMissingSouls(island)) {
			double distance = soul.distSqr(playerPos);
			if (distance < nearestDistance) {
				nearest = soul;
				nearestDistance = distance;
			}
		}
		return nearest;
	}
}
