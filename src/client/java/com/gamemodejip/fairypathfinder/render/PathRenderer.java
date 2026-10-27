package com.gamemodejip.fairypathfinder.render;

import com.gamemodejip.fairypathfinder.navigation.SoulNavigator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the navigation in the world: a box around the target soul and a line from the player to it.
 *
 * Uses Minecraft's built-in Gizmos (debug drawing). Gizmos can only be created while Minecraft is
 * collecting them, otherwise the game throws an IllegalStateException. The client tick is one of
 * those moments, so all drawing happens from END_CLIENT_TICK. Don't move it to another event
 * without checking that first.
 */
public final class PathRenderer {
	/** Colour as ARGB: fully opaque pink, like a fairy soul. */
	private static final int COLOR = 0xFFFF55FF;
	private static final float LINE_WIDTH = 3.0F;

	private PathRenderer() {
	}

	/** Starts drawing every tick. Call once when the mod starts. */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> draw(client));
	}

	/** Draws the box and line for this tick. Minecraft keeps showing them until the next tick. */
	private static void draw(Minecraft client) {
		BlockPos target = SoulNavigator.findNearestMissingSoul();
		if (target == null || client.player == null) {
			return;
		}

		Vec3 from = client.player.position().add(0, 0.1, 0); // just above the feet
		Vec3 to = Vec3.atCenterOf(target);

		Gizmos.line(from, to, COLOR, LINE_WIDTH).setAlwaysOnTop();
		Gizmos.cuboid(target, GizmoStyle.stroke(COLOR)).setAlwaysOnTop();
	}
}
