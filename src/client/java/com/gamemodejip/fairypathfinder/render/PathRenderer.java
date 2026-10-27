package com.gamemodejip.fairypathfinder.render;

import com.gamemodejip.fairypathfinder.navigation.PathFinder;
import com.gamemodejip.fairypathfinder.navigation.SoulNavigator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

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

	/** The first part of the route (where to go right now) is drawn brighter and thicker. */
	private static final int NEAR_COLOR = 0xFFFFCCFF;
	private static final float NEAR_LINE_WIDTH = 6.0F;
	/** How many route steps count as "near". */
	private static final int NEAR_STEPS = 8;

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

		Gizmos.cuboid(target, GizmoStyle.stroke(COLOR)).setAlwaysOnTop();

		// TEMP (debug): cyan dots on every spot the search could reach, when it ran out of paths
		if (SoulNavigator.lastExploredArea != null) {
			for (BlockPos pos : SoulNavigator.lastExploredArea) {
				Gizmos.point(Vec3.atBottomCenterOf(pos).add(0, 0.2, 0), 0xFF55FFFF, 4.0F).setAlwaysOnTop();
			}
		}

		PathFinder.Path path = SoulNavigator.getPath();
		if (path == null) {
			// No route found: fall back to a straight line so the player still knows the direction
			Vec3 from = client.player.position().add(0, 0.1, 0); // just above the feet
			Gizmos.line(from, Vec3.atCenterOf(target), COLOR, LINE_WIDTH).setAlwaysOnTop();
			return;
		}

		List<BlockPos> route = path.route();

		// Connect the route positions with line segments, slightly above the floor.
		// The first NEAR_STEPS segments are highlighted so it's clear which way to go next.
		for (int i = 0; i < route.size() - 1; i++) {
			Vec3 from = Vec3.atBottomCenterOf(route.get(i)).add(0, 0.1, 0);
			Vec3 to = Vec3.atBottomCenterOf(route.get(i + 1)).add(0, 0.1, 0);
			boolean near = i < NEAR_STEPS;
			Gizmos.line(from, to, near ? NEAR_COLOR : COLOR, near ? NEAR_LINE_WIDTH : LINE_WIDTH).setAlwaysOnTop();
		}
		BlockPos routeEnd = route.get(route.size() - 1);
		if (path.complete()) {
			// Last bit: from the end of the route up to the soul (the soul is visible from there)
			Gizmos.line(Vec3.atBottomCenterOf(routeEnd).add(0, 0.1, 0), Vec3.atCenterOf(target), COLOR, LINE_WIDTH).setAlwaysOnTop();
		} else {
			// Partial route: don't draw through walls; mark where the known part ends instead
			Gizmos.cuboid(routeEnd, GizmoStyle.stroke(NEAR_COLOR)).setAlwaysOnTop();
		}
	}
}
