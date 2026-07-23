package net.osmand.search.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import net.osmand.util.MapUtils;

public class HashSkipTileQuadTree<T> {

	public record QuadTreePair<T, R>(T o1, R o2) {
	}

	public static final int MAX_ZOOM = 16;
	public static final int MIN_ZOOM = 0;
	public static final int[] INDEXED_ZOOMS = new int[] { 1, 3, 5, 8, 11, 14, 16 };

	private final List<TileEntry<T>> tileEntries = new ArrayList<>();
	private final ZoomBucket[] zoomBuckets = new ZoomBucket[MAX_ZOOM + 1];

	public static class TileEntry<T> {
		public final long objId;
		public final T obj;
		public final int[] bbox31;
		public final int z;
		public final long tileId;
		public int skipNextTileId = 0;

		TileEntry(long objId, T obj, int[] bbox31, int z, long tileId) {
			this.objId = objId;
			this.obj = obj;
			this.bbox31 = bbox31;
			this.z = z;
			this.tileId = tileId;
		}
	}

	static class ZoomBucketIndexTreeIterator {

		private final ZoomBucketIndexTree[] nodes;
		private final int[] blockIndices;

		public ZoomBucketIndexTreeIterator(ZoomBucket bucket) {
			int depth = bucket.indexedZooms.length;
			this.nodes = new ZoomBucketIndexTree[depth];
			this.blockIndices = new int[depth];
			ZoomBucketIndexTree curr = bucket.indx;
			for (int i = 0; i < depth; i++) {
				nodes[i] = curr;
				blockIndices[i] = 0;
				curr = (curr != null) ? curr.subTree : null;
			}
		}

		public int checkSkip(int currentIndex, long tileId, int tileZoom, int[] queryBBox, int endIndex,
				SkipStats stats) {
			sync(currentIndex);
			for (int level = 0; level < nodes.length; level++) {
				int indxZoom = nodes[level].indxZoom;
				long parentTileId = tileId >> ((tileZoom - indxZoom) * 2);
				if (!intersectsTile(parentTileId, indxZoom, queryBBox)) {
					int nextIndex = skipBlock(nodes[0], level, 0, endIndex);
					if (stats != null) {
						int skipped = nextIndex - currentIndex;
						int tileX = (int) MapUtils.deinterleaveX(parentTileId);
						int tileY = (int) MapUtils.deinterleaveY(parentTileId);
						stats.recordSkip(level, skipped, indxZoom, tileX, tileY);
					}
					return nextIndex;
				}
			}
			return -1;
		}

		public void sync(int globalIndex) {
			for (int level = 0; level < nodes.length; level++) {
				syncSingleLevel(level, globalIndex);
			}
		}

		private void syncSingleLevel(int level, int globalIndex) {
			ZoomBucketIndexTree tree = nodes[level];
			int blockIdx = blockIndices[level];
			TIntArrayList skipIndexes = tree.skipIndexes;
			while (blockIdx + 1 < skipIndexes.size() && skipIndexes.get(blockIdx + 1) <= globalIndex) {
				blockIdx++;
			}
			blockIndices[level] = blockIdx;
		}

		private int skipBlock(ZoomBucketIndexTree tree, int targetLevel, int currentLevel, int endIndex) {
			if (currentLevel < targetLevel) {
				int nextGlobalIndex = skipBlock(tree.subTree, targetLevel, currentLevel + 1, endIndex);
				if (nextGlobalIndex != endIndex) {
					syncSingleLevel(currentLevel, nextGlobalIndex);
				}
				return nextGlobalIndex;
			}
			int nextBlockIdx = blockIndices[targetLevel] + 1;
			blockIndices[targetLevel] = nextBlockIdx;
			if (nextBlockIdx >= tree.skipIndexes.size()) {
				return endIndex;
			}

			int nextGlobalIndex = tree.skipIndexes.get(nextBlockIdx);
			resetChildren(tree, targetLevel, nextBlockIdx);
			return nextGlobalIndex;
		}

		private void resetChildren(ZoomBucketIndexTree tree, int level, int blockIdx) {
			if (tree == null || tree.subTree == null || level + 1 >= nodes.length) {
				return;
			}
			if (blockIdx < tree.skipInSubIndexes.size()) {
				int subBlockIdx = tree.skipInSubIndexes.get(blockIdx);
				blockIndices[level + 1] = subBlockIdx;
				resetChildren(tree.subTree, level + 1, subBlockIdx);
			}
		}
	}

	public static class SkipStats {
		public record SkipRecord(int level, int zoom, int x, int y, int skippedElements) {
		}

		public TLongArrayList inspectedEntries = new TLongArrayList();
		public List<SkipRecord> skipRecords = new ArrayList<>();
		public int totalSkipsCount = 0;
		public long totalElementsSkipped = 0;

		public final int[] skipsPerLevel;
		public final long[] elementsSkippedPerLevel;

		public SkipStats(int levelsCount) {
			this.skipsPerLevel = new int[levelsCount];
			this.elementsSkippedPerLevel = new long[levelsCount];
		}

		public void recordSkip(int level, int skippedElements, int zoom, int x, int y) {
			skipsPerLevel[level]++;
			elementsSkippedPerLevel[level] += skippedElements;
			totalSkipsCount++;
			totalElementsSkipped += skippedElements;
			skipRecords.add(new SkipRecord(level, zoom, x, y, skippedElements));
		}

		public void recordInspection(TileEntry<?> t) {
			inspectedEntries.add(t.objId);
		}

		public void printStats(int totalBucketLen, int[] indexedZooms) {
			System.out.println("=== TileIterator Skip Stats ===");
			System.out.printf("Total bucket size  : %d\n", totalBucketLen);
			System.out.printf("Inspected entries  : %d (%.2f%%)\n", inspectedEntries,
					(double) inspectedEntries.size() / totalBucketLen * 100.0);
			System.out.printf("Total skips count  : %d\n", totalSkipsCount);
			System.out.printf("Total skipped items: %d\n", totalElementsSkipped);

			for (int level = 0; level < skipsPerLevel.length; level++) {
				int zoom = (indexedZooms != null && level < indexedZooms.length) ? indexedZooms[level] : level;
				System.out.printf("  Level %d (Z%d): %d skips, %d items skipped\n", level, zoom, skipsPerLevel[level],
						elementsSkippedPerLevel[level]);
			}
		}
	}

	static class ZoomBucketIndexTree {
		public final int indxZoom;
		public final ZoomBucketIndexTree subTree;
		// start index in global list
		public TIntArrayList skipIndexes = new TIntArrayList();
		// start index in sub tree
		public TIntArrayList skipInSubIndexes = new TIntArrayList();
		// for debug only
		public TLongArrayList tileIds = new TLongArrayList();
		// used only for build
		long lastTileId = -1;

		public ZoomBucketIndexTree(int indZoom, int[] indexedZooms) {
			this.indxZoom = indexedZooms[indZoom];
			if (indZoom < indexedZooms.length - 1) {
				subTree = new ZoomBucketIndexTree(indZoom + 1, indexedZooms);
			} else {
				subTree = null;
			}
		}

		public boolean processEntry(int tileIdZoom, long tileId, int globalIndex) {
			boolean changed = false;
			long currentParentTileId = tileId >> ((tileIdZoom - this.indxZoom) * 2);
			if (currentParentTileId != this.lastTileId) {
				if (subTree != null) {
					this.skipInSubIndexes.add(subTree.skipIndexes.size());
				}
				this.skipIndexes.add(globalIndex);
				this.lastTileId = currentParentTileId;
				this.tileIds.add(currentParentTileId);
				changed = true;
			}
			if (this.subTree != null) {
				this.subTree.processEntry(tileIdZoom, tileId, globalIndex);
			}
			return changed;
		}

	}

	static class ZoomBucket {
		public int z;
		public int start;
		public int len;
		public int[] indexedZooms;

		public ZoomBucketIndexTree indx = null;

		ZoomBucket(int z, int start, int[] indexedZooms) {
			this.z = z;
			this.start = start;
			this.indexedZooms = indexedZooms;
			if (indexedZooms.length > 0) {
				indx = new ZoomBucketIndexTree(0, indexedZooms);
			}
		}
	}

	public void addObject(T obj, int[] bbox31, long externalId) {
		long objId = externalId == -1 ? tileEntries.size() : externalId;
		int targetZoom = MAX_ZOOM;
		// Fit into 2x2 - maximum 4 tiles
		while (targetZoom > MIN_ZOOM) {
			int shift = 31 - targetZoom;
			int minX = bbox31[0] >> shift;
			int maxX = bbox31[2] >> shift;
			int minY = bbox31[1] >> shift;
			int maxY = bbox31[3] >> shift;
			if ((maxX - minX <= 1) && (maxY - minY <= 1)) {
				if ((minX >> 1) == (maxX >> 1) && (minY >> 1) == (maxY >> 1)) {
					targetZoom--;
				}
				break;
			}
			targetZoom--;
		}
		addTileEntry(obj, bbox31, objId, targetZoom);
	}

	public static long encodeTileId(int x, int y) {
		return MapUtils.interleaveBits(x, y);
	}

	private static boolean intersectsBBox(int[] a, int[] b) {
		return a[0] <= b[2] && a[2] >= b[0] && a[1] <= b[3] && a[3] >= b[1];
	}

	private void addTileEntry(T obj, int[] bbox31, long objId, int targetZoom) {
		int shift = 31 - targetZoom;
		int minX = bbox31[0] >> shift;
		int maxX = bbox31[2] >> shift;
		int minY = bbox31[1] >> shift;
		int maxY = bbox31[3] >> shift;
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				tileEntries.add(new TileEntry<>(objId, obj, bbox31, targetZoom, encodeTileId(x, y)));
			}
		}
	}

	public void build() {
		tileEntries.sort((e1, e2) -> {
			if (e1.z != e2.z)
				return Integer.compare(e1.z, e2.z);
			return Long.compare(e1.tileId, e2.tileId);
		});
		ZoomBucket zoomBucket = null;
		int lastZoom = -1;
		long lastTileId = -1;
		int lastTileIdFirstInd = -1;
		int index = 0;
		for (index = 0; index < tileEntries.size(); index++) {
			TileEntry<T> tileE = tileEntries.get(index);
			long tileId = tileE.tileId;
			int tileZoom = tileE.z;
			if (lastTileId != tileId || lastZoom != tileZoom) {
				if (lastTileIdFirstInd >= 0) {
					tileEntries.get(lastTileIdFirstInd).skipNextTileId = index;
				}
				lastTileId = tileId;
				lastTileIdFirstInd = index;
			}
			if (lastZoom != tileZoom) {
				lastZoom = tileZoom;
				TIntArrayList indexedZooms = new TIntArrayList();
				for (int iz : INDEXED_ZOOMS) {
					if (iz >= tileE.z) {
						break;
					}
					indexedZooms.add(iz);
				}
				zoomBucket = new ZoomBucket(lastZoom, index, indexedZooms.toArray());
				zoomBuckets[tileE.z] = zoomBucket;
			}
			if (zoomBucket.indx != null) {
				zoomBucket.indx.processEntry(lastZoom, tileId, index);
			}

			zoomBucket.len++;
		}
		if (lastTileIdFirstInd >= 0) {
			tileEntries.get(lastTileIdFirstInd).skipNextTileId = index;
		}
	}

	private static boolean intersectsTile(long parentTileId, int parentZoom, int[] queryBBox) {
		int shift = 31 - parentZoom;

		int tileX = (int) MapUtils.deinterleaveX(parentTileId);
		int tileY = (int) MapUtils.deinterleaveY(parentTileId);
		int tileMinX = tileX << shift;
		int tileMaxX = ((tileX + 1) << shift) - 1;
		int tileMinY = tileY << shift;
		int tileMaxY = ((tileY + 1) << shift) - 1;
		return tileMinX <= queryBBox[2] && tileMaxX >= queryBBox[0] && tileMinY <= queryBBox[3]
				&& tileMaxY >= queryBBox[1];
	}

	class TileIterator implements Iterator<TileEntry<T>> {
		final int[] queryBBox;
		final ZoomBucket bucket;
		final int endIndex;
		final ZoomBucketIndexTreeIterator treeIt;

		int currentIndex;
		TileEntry<T> nextEntry;

		final SkipStats stats;

		public TileIterator(ZoomBucket bucket, int[] queryBBox, SkipStats stats) {
			this.bucket = bucket;
			this.queryBBox = queryBBox;
			this.currentIndex = bucket.start;
			this.endIndex = bucket.start + bucket.len;
			this.treeIt = new ZoomBucketIndexTreeIterator(bucket);
			this.stats = stats;
			advance();
		}

		@Override
		public boolean hasNext() {
			return nextEntry != null;
		}

		@Override
		public TileEntry<T> next() {
			if (nextEntry == null) {
				throw new NoSuchElementException();
			}
			TileEntry<T> current = nextEntry;
			advance();
			return current;
		}

		private void advance() {
			nextEntry = null;
			while (currentIndex < endIndex) {
				TileEntry<T> entry = tileEntries.get(currentIndex);
				int newIndex = treeIt.checkSkip(currentIndex, entry.tileId, entry.z, queryBBox, endIndex, stats);
				if (newIndex >= 0) {
					currentIndex = newIndex;
					continue;
				}
				stats.recordInspection(entry);
				if (!intersectsTile(entry.tileId, entry.z, queryBBox)) {
					currentIndex = entry.skipNextTileId > 0 ? entry.skipNextTileId : currentIndex + 1;
					continue;
				}
				if (intersectsBBox(entry.bbox31, queryBBox)) {
					nextEntry = entry;
					currentIndex++;
					return;
				}
				currentIndex++;
			}
		}
	}

	public ZoomBucket getZoomBucket(int z) {
		return zoomBuckets[z];
	}

	public List<TileEntry<T>> get(int[] queryBBox, SkipStats stats) {
		List<TileEntry<T>> res = new ArrayList<>();
		for (int z = MIN_ZOOM; z <= MAX_ZOOM; z++) {
			ZoomBucket zoomBucket = zoomBuckets[z];
			if (zoomBucket != null) {
				TileIterator ti = new TileIterator(zoomBucket, queryBBox, stats);
				while (ti.hasNext()) {
					res.add(ti.next());
				}
			}
		}
		return res;
	}

}