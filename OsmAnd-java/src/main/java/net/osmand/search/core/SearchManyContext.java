package net.osmand.search.core;

import java.util.List;

import net.osmand.binary.BinaryMapIndexReader;

public class SearchManyContext {
	List<BinaryMapIndexReader> files;
	SearchManyStats stats = new SearchManyStats();

	public static class SearchManyStats {
		long time = System.nanoTime();
		long readTime = 0;
		long computeTime = 0;

		@Override
		public String toString() {
			return String.format("Search Stats %.1f ms - read %.1f ms, comp %.1f ms", time / 1e6, readTime / 1e6,
					computeTime / 1e6);
		}

		public void finish() {
			time = System.nanoTime() - time;
		}
	}

}