package net.osmand.search.core;

import java.util.List;
import net.osmand.search.core.HashSkipTileQuadTree.TileEntry;
import net.osmand.search.core.HashSkipTileQuadTree.ZoomBucket;
import net.osmand.search.core.HashSkipTileQuadTree.ZoomBucketIndexTreeIterator;
import net.osmand.util.MapUtils;

/**
 * High-performance spatial join algorithm leveraging
 * {@link ZoomBucketIndexTreeIterator} for fast block skipping between different
 * zoom buckets (e.g. Z3 vs Z15).
 *
 * @param <T> Payload type for the first tree.
 * @param <R> Payload type for the second tree.
 */
public class HashSkipTileQuadTreeJoiner<T, R> {

	@FunctionalInterface
	public interface JoinCallback<T, R> {
		void onIntersection(TileEntry<T> e1, TileEntry<R> e2);
	}

	private final HashSkipTileQuadTree<T> tree1;
	private final HashSkipTileQuadTree<R> tree2;

	public HashSkipTileQuadTreeJoiner(HashSkipTileQuadTree<T> tree1, HashSkipTileQuadTree<R> tree2) {
		this.tree1 = tree1;
		this.tree2 = tree2;
	}

	/**
	 * Performs spatial join across all zoom bucket pairs between tree1 and tree2.
	 */
	public void joinAllBuckets(JoinCallback<T, R> callback, HashSkipTileQuadTree.SkipStats stats1,
			HashSkipTileQuadTree.SkipStats stats2) {
		for (int z1 = HashSkipTileQuadTree.MIN_ZOOM; z1 <= HashSkipTileQuadTree.MAX_ZOOM; z1++) {
			ZoomBucket b1 = tree1.getZoomBucket(z1);
			if (b1 == null || b1.len == 0) {
				continue;
			}
			for (int z2 = HashSkipTileQuadTree.MIN_ZOOM; z2 <= HashSkipTileQuadTree.MAX_ZOOM; z2++) {
				ZoomBucket b2 = tree2.getZoomBucket(z2);
				if (b2 == null || b2.len == 0) {
					continue;
				}
				joinBuckets(z1, z2, callback, stats1, stats2);
			}
		}
	}

	/**
	 * Joins two specific zoom buckets z1 and z2 using tree skipping iterators.
	 */
	public void joinBuckets(int z1, int z2, JoinCallback<T, R> callback, HashSkipTileQuadTree.SkipStats stats1,
			HashSkipTileQuadTree.SkipStats stats2) {
		ZoomBucket b1 = tree1.getZoomBucket(z1);
		ZoomBucket b2 = tree2.getZoomBucket(z2);

		if (b1 == null || b2 == null || b1.len == 0 || b2.len == 0) {
			return;
		}

		if (z1 > z2) {
			this.<R, T>joinBucketsOrdered(b2, tree2.getTileEntries(), b1, tree1.getTileEntries(),
					(e2, e1) -> callback.onIntersection(e1, e2), stats2, stats1);
		} else {
			this.<T, R>joinBucketsOrdered(b1, tree1.getTileEntries(), b2, tree2.getTileEntries(), callback, stats1,
					stats2);
		}
	}

	/**
	 * Core join method assuming bA.z <= bB.z. Utilizes ZoomBucketIndexTreeIterator
	 * on bucket B to skip large non-intersecting tile blocks.
	 */
	private <A, B> void joinBucketsOrdered(ZoomBucket bA, List<TileEntry<A>> entriesA, ZoomBucket bB,
			List<TileEntry<B>> entriesB, JoinCallback<A, B> callback, HashSkipTileQuadTree.SkipStats statsA,
			HashSkipTileQuadTree.SkipStats statsB) {
		int deltaZ = bB.z - bA.z; // deltaZ >= 0
		int shiftBits = deltaZ * 2;

		int iA = bA.start;
		int endA = bA.start + bA.len;

		int iB = bB.start;
		int endB = bB.start + bB.len;

		// Iterator over higher-zoom tree nodes to enable indexed skips
		ZoomBucketIndexTreeIterator treeItB = new ZoomBucketIndexTreeIterator(bB);

		while (iA < endA && iB < endB) {
			TileEntry<A> eA = entriesA.get(iA);
			long tileIdA = eA.tileId;

			TileEntry<B> eB = entriesB.get(iB);

			// 1. Check tree index skip on Bucket B against current tile A's 31-bit BBox
			int[] tileABBox31 = calculateTileBBox31(tileIdA, bA.z);
			int newIB = treeItB.checkSkip(iB, eB.tileId, eB.z, tileABBox31, endB, statsB);

			if (newIB >= 0) {
				iB = newIB;
				continue;
			}

			long parentTileIdB = eB.tileId >> shiftBits;

			if (parentTileIdB < tileIdA) {
				// eB lags behind eA spatially -> advance iB using next tile pointer
				if (statsB != null) {
					statsB.recordInspection(eB);
				}
				iB = eB.skipNextTileId > 0 ? eB.skipNextTileId : iB + 1;
			} else if (parentTileIdB > tileIdA) {
				// eB has passed eA spatially -> skip all entries in bA sharing current tileIdA
				int nextIA = eA.skipNextTileId > 0 ? eA.skipNextTileId : iA + 1;
				if (statsA != null) {
					int skipped = nextIA - iA;
					int tileX = (int) MapUtils.deinterleaveX(tileIdA);
					int tileY = (int) MapUtils.deinterleaveY(tileIdA);
					statsA.recordSkip(bA.z, 0, skipped, bA.z, tileX, tileY);
				}
				iA = nextIA;
			} else {
				// Match found! parentTileIdB == tileIdA
				if (statsA != null) {
					statsA.recordInspection(eA);
				}

				int matchStartB = iB;

				// Iterate over all entries in Bucket A sharing tileIdA
				while (iA < endA && entriesA.get(iA).tileId == tileIdA) {
					TileEntry<A> curEA = entriesA.get(iA);
					int curIB = matchStartB;

					while (curIB < endB) {
						TileEntry<B> curEB = entriesB.get(curIB);
						long curParentB = curEB.tileId >> shiftBits;

						if (curParentB != tileIdA) {
							break; // End of matching parent tile scope
						}

						if (statsB != null) {
							statsB.recordInspection(curEB);
						}

						// Precise 31-bit bounding box intersection check
						if (intersectsBBox(curEA.bbox31, curEB.bbox31)) {
							callback.onIntersection(curEA, curEB);
						}

						curIB++;
					}
					iA++;
				}

				// Advance iB past all entries matching current parentTileIdB
				iB = advanceToNextParentTile(entriesB, matchStartB, endB, shiftBits, tileIdA);
			}
		}
	}

	private <X> int advanceToNextParentTile(List<TileEntry<X>> entries, int start, int end, int shiftBits,
			long currentParentTileId) {
		int curr = start;
		while (curr < end) {
			long parentTileId = entries.get(curr).tileId >> shiftBits;
			if (parentTileId != currentParentTileId) {
				return curr;
			}
			curr = entries.get(curr).skipNextTileId > 0 ? entries.get(curr).skipNextTileId : curr + 1;
		}
		return end;
	}

	private static int[] calculateTileBBox31(long tileId, int zoom) {
		int shift = 31 - zoom;
		int tileX = (int) MapUtils.deinterleaveX(tileId);
		int tileY = (int) MapUtils.deinterleaveY(tileId);

		int minX = tileX << shift;
		int maxX = ((tileX + 1) << shift) - 1;
		int minY = tileY << shift;
		int maxY = ((tileY + 1) << shift) - 1;

		return new int[] { minX, minY, maxX, maxY };
	}

	private static boolean intersectsBBox(int[] a, int[] b) {
		return a[0] <= b[2] && a[2] >= b[0] && a[1] <= b[3] && a[3] >= b[1];
	}
}