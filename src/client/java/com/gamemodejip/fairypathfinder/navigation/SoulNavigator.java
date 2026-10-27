package com.gamemodejip.fairypathfinder.navigation;

import com.gamemodejip.fairypathfinder.FairyPathfinderClient;
import com.gamemodejip.fairypathfinder.location.IslandDetector;
import com.gamemodejip.fairypathfinder.tracking.FoundSoulTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Decides which Fairy Soul to navigate to (the nearest missing one on the current island) and
 * keeps the route to it up to date.
 *
 * A search is expensive, so a finished route is kept and trimmed as the player follows it: every
 * tick the part already behind the player is cut off. A new search only starts when the soul
 * changes, the player leaves the route, or the player nears the end of a partial route.
 */
public final class SoulNavigator {
	/** How close (in blocks) the route has to get to the soul. You can click a soul from ~4.5 blocks. */
	private static final double REACH = 4.0;
	/** Further than this (in blocks) from every point of the route counts as "left the route". */
	private static final double OFF_ROUTE_DISTANCE = 4.0;
	/** For a partial route: search again once the player is this many steps from its end. */
	private static final int CONTINUE_NEAR_END_STEPS = 10;
	/** Wait at least this many ticks between searches (except when the soul changes): 1 second. */
	private static final int MIN_TICKS_BETWEEN_SEARCHES = 20;
	/**
	 * One background thread for searching, so the game never waits for it. Daemon: it doesn't keep
	 * the game running on exit. Low priority: the game itself always goes first.
	 */
	private static final ExecutorService SEARCH_THREAD = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "FairyPathfinder-Search");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	});

	private static BlockPos lastTarget = null;
	/** The route as the last search found it. */
	private static PathFinder.Path fullPath = null;
	/** fullPath with the part behind the player cut off: what gets drawn. */
	private static PathFinder.Path path = null;
	private static int ticksSinceSearch = 0;
	/** The search that's still running on the background thread, or null. */
	private static PathFinder.Search runningSearch = null;
	/** Becomes done when runningSearch has finished. */
	private static Future<?> runningTask = null;
	/** TEMP (debug): info about the last finished search, shown on the HUD. */
	public static String lastSearchInfo = "";
	/** TEMP (debug): the area the last search could reach, if it ran out of paths (else null). */
	public static List<BlockPos> lastExploredArea = null;

	private SoulNavigator() {
	}

	/** The current (trimmed) path, without doing any work. Safe to call every frame. */
	public static PathFinder.Path getCurrentPath() {
		return path;
	}

	/** The soul being navigated to, or null. Safe to call every frame. */
	public static BlockPos getCurrentTarget() {
		return lastTarget;
	}

	/**
	 * The path from the player to the nearest missing soul, or null if there's no target or no path yet.
	 * Call once per tick: continues the running search a bit, trims the route to the player's
	 * position, and starts a new search when needed.
	 */
	public static PathFinder.Path getPath() {
		var client = Minecraft.getInstance();
		BlockPos target = findNearestMissingSoul();
		if (target == null || client.player == null || client.level == null) {
			lastTarget = null;
			fullPath = null;
			path = null;
			stopSearch();
			return null;
		}

		boolean targetChanged = !target.equals(lastTarget);
		if (targetChanged) {
			lastTarget = target;
			fullPath = null; // the old route leads to the old soul
			path = null;
			stopSearch();
		}

		continueSearch(client.level);

		BlockPos player = client.player.blockPosition();
		boolean onRoute = trimToPlayer(player);

		ticksSinceSearch++;
		boolean nearEndOfPartial = fullPath != null && !fullPath.complete() && path != null
				&& path.route().size() <= CONTINUE_NEAR_END_STEPS;
		boolean needSearch = targetChanged
				|| (ticksSinceSearch >= MIN_TICKS_BETWEEN_SEARCHES && (fullPath == null || !onRoute || nearEndOfPartial));
		if (runningSearch == null && needSearch) {
			startSearch(new PathFinder.Search(client.level, player, target, REACH));
		}
		return path;
	}

	/** Starts a search on the background thread. */
	private static void startSearch(PathFinder.Search search) {
		runningSearch = search;
		runningTask = SEARCH_THREAD.submit(search::runToEnd);
		ticksSinceSearch = 0;
	}

	/** Cancels the running search, if any. */
	private static void stopSearch() {
		if (runningSearch != null) {
			runningSearch.cancel();
		}
		runningSearch = null;
		runningTask = null;
	}

	/** Checks whether the background search has finished; if so, takes over its route. */
	private static void continueSearch(BlockGetter level) {
		if (runningSearch == null) {
			return;
		}
		if (runningSearch.level() != level) {
			stopSearch(); // warped to another island
			return;
		}
		if (!runningTask.isDone()) {
			return;
		}
		try {
			runningTask.get(); // rethrows anything that went wrong on the background thread
			lastSearchInfo = runningSearch.debugInfo(); // TEMP (debug)
			lastExploredArea = runningSearch.ranOutOfPaths() && runningSearch.examinedCount() <= 5_000
					? runningSearch.examined() : null; // TEMP (debug)
			fullPath = runningSearch.result();
		} catch (InterruptedException | ExecutionException e) {
			// Most likely the world changed while being read; MIN_TICKS_BETWEEN_SEARCHES later we retry
			FairyPathfinderClient.LOGGER.debug("Fairy soul search failed, will retry", e);
		}
		runningSearch = null;
		runningTask = null;
	}

	/**
	 * Cuts off the part of the route that's behind the player: finds the route point closest to
	 * the player and keeps the route from there on.
	 *
	 * @return true if the player is on the route (within OFF_ROUTE_DISTANCE of it)
	 */
	private static boolean trimToPlayer(BlockPos player) {
		if (fullPath == null) {
			path = null;
			return false;
		}
		List<BlockPos> route = fullPath.route();
		int closestIndex = 0;
		double closestDistance = Double.MAX_VALUE;
		for (int i = 0; i < route.size(); i++) {
			double distance = route.get(i).distSqr(player);
			if (distance < closestDistance) {
				closestIndex = i;
				closestDistance = distance;
			}
		}
		if (closestDistance > OFF_ROUTE_DISTANCE * OFF_ROUTE_DISTANCE) {
			return false; // keep showing the old route until the new search is done
		}
		path = new PathFinder.Path(route.subList(closestIndex, route.size()), fullPath.complete());
		return true;
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
