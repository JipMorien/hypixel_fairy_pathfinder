package com.gamemodejip.fairypathfinder.render;

import com.gamemodejip.fairypathfinder.FairyPathfinderClient;
import com.gamemodejip.fairypathfinder.navigation.PathFinder;
import com.gamemodejip.fairypathfinder.navigation.SoulNavigator;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Shows an arrow under the crosshair that points where to walk, plus the distance to the soul,
 * e.g. "↗ 34m ▲6". The arrow follows the route a few steps ahead, so it tells you left/right
 * even when the line in the world is hard to read.
 */
public final class DirectionHud {
	private static final int COLOR = 0xFFFF55FF;
	/** Look this many route steps ahead to decide the direction. */
	private static final int LOOK_AHEAD_STEPS = 6;
	/** 8 arrows, one per 45 degrees, starting with "straight ahead" and going clockwise. */
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private DirectionHud() {
	}

	/** Adds the arrow to the HUD, drawn right after the crosshair. Call once when the mod starts. */
	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR,
				Identifier.fromNamespaceAndPath(FairyPathfinderClient.MOD_ID, "direction"),
				DirectionHud::draw);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		var client = Minecraft.getInstance();
		BlockPos target = SoulNavigator.getCurrentTarget();
		if (client.player == null || target == null) {
			return;
		}

		// Aim at a point a few steps ahead on the route, or at the soul if there's no route
		PathFinder.Path path = SoulNavigator.getCurrentPath();
		List<BlockPos> route = path == null ? null : path.route();
		BlockPos aim = route == null || route.size() < 2 ? target : route.get(Math.min(LOOK_AHEAD_STEPS, route.size() - 1));

		double dx = aim.getX() + 0.5 - client.player.getX();
		double dz = aim.getZ() + 0.5 - client.player.getZ();
		// Minecraft yaw: 0 = south (+Z), 90 = west (-X), so this gives the yaw that faces the aim point
		float aimYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		// Relative angle: 0 = straight ahead, positive = to the right, negative = to the left
		float relative = Mth.wrapDegrees(aimYaw - client.player.getYRot());
		String arrow = ARROWS[Math.floorMod(Math.round(relative / 45f), 8)];

		int distance = (int) Math.round(Math.sqrt(client.player.blockPosition().distSqr(target)));
		int heightDifference = target.getY() - client.player.blockPosition().getY();
		String height = heightDifference > 2 ? " ▲" + heightDifference : heightDifference < -2 ? " ▼" + (-heightDifference) : "";

		// "…" = the route only covers part of the way so far
		String partial = path != null && !path.complete() ? " …" : "";

		graphics.centeredText(client.font, arrow + " " + distance + "m" + height + partial,
				graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 12, COLOR);
		// TEMP (debug)
		graphics.centeredText(client.font, SoulNavigator.lastSearchInfo,
				graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 24, 0xFFAAAAAA);
	}
}
