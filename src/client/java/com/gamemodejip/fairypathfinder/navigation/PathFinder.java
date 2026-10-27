package com.gamemodejip.fairypathfinder.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * Finds a route through open space in the loaded world with the A* algorithm.
 * The route doesn't have to be walkable: between jumping, dropping, AOTE and ways to cancel fall
 * damage, a player can get almost anywhere there is room. So the rule is simply "is there space
 * for the player here" (2 blocks of free space), in every direction including up and down.
 * Positions are "foot positions": the lower of the two free blocks.
 */
public final class PathFinder {
	/**
	 * Extra cost per block of going up. Going up is the hardest direction without a floor (it takes
	 * jumps, ladders or an AOTE), so routes that stay level or go down are preferred when they're
	 * about as long.
	 */
	private static final double UP_PENALTY = 1.0;

	/**
	 * Safety limit: how many positions A* may examine before giving up. Keeps the game from
	 * freezing when the soul is unreachable or very far away.
	 */
	private static final int MAX_EXPANDED_NODES = 80_000;

	/**
	 * Weighted A*: the distance estimate counts this much more than the distance walked.
	 * Makes the search head for the soul much more directly, so it finds routes far more often
	 * within the limit. Trade-off: the route can be a bit longer than the shortest possible.
	 */
	private static final double HEURISTIC_WEIGHT = 2.5;

	/** Within this distance the soul always counts as clickable, even if a block is in the line of sight. */
	private static final double ALWAYS_REACHABLE = 2.5;

	/** Height of the player's eyes above their feet. */
	private static final double EYE_HEIGHT = 1.62;

	private PathFinder() {
	}

	/** One entry in the A* queue: a position and its estimated total cost (f = g + h). */
	private record Node(BlockPos pos, double estimatedTotal) {
	}

	/**
	 * A search result.
	 *
	 * @param route    positions from start to end (both included)
	 * @param complete true if the route reaches a spot where the soul can be clicked; false if it
	 *                 only gets as close as the search could (the rest isn't known yet)
	 */
	public record Path(List<BlockPos> route, boolean complete) {
	}

	/**
	 * One A* search, from start to any position within `reach` blocks of the target from which
	 * the player can actually see the target (no blocks in between).
	 *
	 * SoulNavigator runs it on a background thread, so even a long search never slows the game
	 * down. It only reads the world, never changes it. Reading Minecraft's world from another
	 * thread isn't officially supported: rarely a block read while the game changes that chunk
	 * gives outdated data or an exception. The caller catches that and simply searches again.
	 * Call cancel() to stop a search that's no longer needed.
	 *
	 * If the goal can't be reached (too far, or the area isn't loaded yet), the result is the
	 * route to the examined position that got closest to the target, marked as not complete.
	 *
	 * Positions are stored as longs (BlockPos.asLong) in fastutil collections, which Minecraft
	 * ships with: far faster and lighter than HashMaps full of BlockPos objects.
	 */
	public static final class Search {
		private static final long NONE = Long.MIN_VALUE; // "no parent" marker in cameFrom

		private final BlockGetter level;
		private final BlockPos start;
		private final BlockPos target;
		private final double reachSqr;

		private final PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.estimatedTotal(), b.estimatedTotal()));
		private final Long2LongOpenHashMap cameFrom = new Long2LongOpenHashMap();       // pos -> the position we came from
		private final Long2DoubleOpenHashMap costSoFar = new Long2DoubleOpenHashMap();  // g: cheapest known cost from start to pos
		private final LongOpenHashSet closed = new LongOpenHashSet();                   // positions already fully examined
		private final Long2BooleanOpenHashMap openCache = new Long2BooleanOpenHashMap(); // isOpen results, each block looked up once

		private BlockPos closest; // the examined position nearest to the target so far
		private boolean finished = false;
		private Path result = null;
		/** TEMP (debug): why the search stopped. */
		private String stopReason = "running";
		/** Set from the game thread when this search is no longer needed. */
		private volatile boolean cancelled = false;

		public Search(BlockGetter level, BlockPos start, BlockPos target, double reach) {
			this.level = level;
			this.start = start;
			this.target = target;
			this.reachSqr = reach * reach;
			this.closest = start;
			cameFrom.defaultReturnValue(NONE);
			costSoFar.defaultReturnValue(Double.MAX_VALUE);
			costSoFar.put(start.asLong(), 0.0);
			open.add(new Node(start, HEURISTIC_WEIGHT * heuristic(start, target)));
		}

		/**
		 * Continues the search for at most `maxNanos` nanoseconds, then pauses.
		 *
		 * @return true when the search is finished (see result())
		 */
		public boolean step(long maxNanos) {
			long deadline = System.nanoTime() + maxNanos;
			int expanded = 0;
			// Checking the clock is relatively slow, so only do it every 32 positions
			while (!finished && (expanded % 32 != 0 || System.nanoTime() < deadline)) {
				if (cancelled) {
					stopReason = "cancelled"; // TEMP (debug)
					result = null;
					finished = true;
					break;
				}
				if (open.isEmpty() || closed.size() >= MAX_EXPANDED_NODES) {
					stopReason = open.isEmpty() ? "no more paths" : "limit reached"; // TEMP (debug)
					// No full route: fall back to the route towards the closest position we found
					result = closest.equals(start) ? null : new Path(buildRoute(closest), false);
					finished = true;
					break;
				}

				BlockPos current = open.poll().pos();
				long currentKey = current.asLong();
				if (!closed.add(currentKey)) {
					continue; // already examined via a cheaper route (duplicates are allowed in the queue)
				}
				expanded++;

				if (current.distSqr(target) <= reachSqr && canSee(level, current, target)) {
					stopReason = "found"; // TEMP (debug)
					result = new Path(buildRoute(current), true);
					finished = true;
					break;
				}
				if (current.distSqr(target) < closest.distSqr(target)) {
					closest = current;
				}

				double currentCost = costSoFar.get(currentKey);
				for (BlockPos next : getNeighbours(current, this::isOpenCached)) {
					long nextKey = next.asLong();
					if (closed.contains(nextKey)) {
						continue;
					}
					double stepCost = Math.sqrt(current.distSqr(next)) + (next.getY() > current.getY() ? UP_PENALTY : 0);
					double newCost = currentCost + stepCost;
					if (newCost < costSoFar.get(nextKey)) {
						costSoFar.put(nextKey, newCost);
						cameFrom.put(nextKey, currentKey);
						open.add(new Node(next, newCost + HEURISTIC_WEIGHT * heuristic(next, target)));
					}
				}
			}
			return finished;
		}

		/** Runs the search until it's finished or cancelled. */
		public void runToEnd() {
			while (!step(50_000_000L)) {
				// step() pauses every 50 ms; just continue (it checks `cancelled` along the way)
			}
		}

		/** Asks the search to stop as soon as possible. Safe to call from any thread. */
		public void cancel() {
			cancelled = true;
		}

		/** isOpen, but remembered per position for the rest of this search. */
		private boolean isOpenCached(BlockPos pos) {
			long key = pos.asLong();
			if (openCache.containsKey(key)) {
				return openCache.get(key);
			}
			boolean result = isOpen(level, pos);
			openCache.put(key, result);
			return result;
		}

		/** Walks back from the goal through cameFrom to the start, then flips the list. */
		private List<BlockPos> buildRoute(BlockPos goal) {
			List<BlockPos> route = new ArrayList<>();
			for (long key = goal.asLong(); key != NONE; key = cameFrom.get(key)) {
				route.add(BlockPos.of(key));
			}
			Collections.reverse(route);
			return route;
		}

		/** The path once step() returned true; null if not a single step was possible. */
		public Path result() {
			return result;
		}

		/** The world this search reads, so the caller can drop it after warping to another island. */
		public BlockGetter level() {
			return level;
		}

		public BlockPos target() {
			return target;
		}

		/** TEMP (debug): every position the search reached. */
		public List<BlockPos> examined() {
			List<BlockPos> positions = new ArrayList<>(closed.size());
			closed.forEach(key -> positions.add(BlockPos.of(key)));
			return positions;
		}

		/** TEMP (debug): true if the search stopped because there was nowhere left to go. */
		public boolean ranOutOfPaths() {
			return stopReason.equals("no more paths");
		}

		/** TEMP (debug): how many positions were examined. */
		public int examinedCount() {
			return closed.size();
		}

		/** TEMP (debug): e.g. "limit reached, 40000 examined, closest 23m from soul". */
		public String debugInfo() {
			return stopReason + ", " + closed.size() + " examined, closest "
					+ Math.round(Math.sqrt(closest.distSqr(target))) + "m from soul";
		}
	}

	/**
	 * Can a player standing at `feet` see the target? Casts a ray from their eyes to the target's
	 * centre. Hitting the target's own block also counts, for souls placed inside a block.
	 */
	private static boolean canSee(BlockGetter level, BlockPos feet, BlockPos target) {
		if (feet.distSqr(target) <= ALWAYS_REACHABLE * ALWAYS_REACHABLE) {
			return true; // right next to it: snow, leaves or a slab in the way don't stop you clicking it
		}
		Vec3 eyes = Vec3.atBottomCenterOf(feet).add(0, EYE_HEIGHT, 0);
		Vec3 soul = Vec3.atCenterOf(target);
		BlockHitResult hit = level.clip(new ClipContext(eyes, soul,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
	}

	/** h: straight-line distance to the target. Never more than the real walking distance. */
	private static double heuristic(BlockPos pos, BlockPos target) {
		return Math.sqrt(pos.distSqr(target));
	}

	/**
	 * Every neighbouring position with room for the player: all 26 directions around this block
	 * (sideways, up, down and diagonal). A diagonal move is only allowed if the straight moves it
	 * combines are open too, so the route never squeezes through the corner between two blocks.
	 */
	static List<BlockPos> getNeighbours(BlockPos feet, Predicate<BlockPos> isOpen) {
		List<BlockPos> neighbours = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					BlockPos next = feet.offset(dx, dy, dz);
					if (!isOpen.test(next)) {
						continue;
					}
					// For diagonal moves, every single-axis part of the move must be open as well
					if (dx != 0 && (dy != 0 || dz != 0) && !isOpen.test(feet.offset(dx, 0, 0))) {
						continue;
					}
					if (dy != 0 && (dx != 0 || dz != 0) && !isOpen.test(feet.offset(0, dy, 0))) {
						continue;
					}
					if (dz != 0 && (dx != 0 || dy != 0) && !isOpen.test(feet.offset(0, 0, dz))) {
						continue;
					}
					neighbours.add(next);
				}
			}
		}
		return neighbours;
	}

	/** Is there room for the whole player here: feet and head both passable? */
	private static boolean isOpen(BlockGetter level, BlockPos feet) {
		return isPassable(level, feet) && isPassable(level, feet.above());
	}

	/**
	 * Can the player's body be in this block? No collision (air, grass, flowers...) or climbable
	 * (ladders have a thin collision shape but you can be inside them), and no lava.
	 * Unloaded chunks and positions outside the world count as blocked: we don't know what's there.
	 */
	private static boolean isPassable(BlockGetter level, BlockPos pos) {
		if (level.isOutsideBuildHeight(pos) || (level instanceof Level world && !world.getChunkSource()
				.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())))) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return (state.getCollisionShape(level, pos).isEmpty() || state.typeHolder().is(BlockTags.CLIMBABLE))
				&& !state.getFluidState().typeHolder().is(FluidTags.LAVA);
	}
}
