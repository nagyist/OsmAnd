package net.osmand.search.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import net.osmand.binary.BinaryMapDataObject;
import net.osmand.map.OsmandRegions;
import net.osmand.util.MapUtils;

public class OsmGpxRegionsJoinTest {

	static class GpxTrackObject {
		final long id;
		final String name;
		final String activity;
		final double speed;
		final String tags;
		final int[] bbox;

		GpxTrackObject(long id, String name, String activity, double speed, String tags, int[] bbox) {
			this.id = id;
			this.name = name;
			this.activity = activity;
			this.speed = speed;
			this.tags = tags;
			this.bbox = bbox;
		}
	}

	static class TrackFilter {
		Set<String> allowedActivities;
		Double maxSpeed;
		String tagContains;

		public boolean test(String activity, double speed, String tags) {
			if (allowedActivities != null && !allowedActivities.contains(activity)) {
				return false;
			}
			if (maxSpeed != null && speed >= maxSpeed) {
				return false;
			}
			if (tagContains != null && (tags == null || !tags.contains(tagContains))) {
				return false;
			}
			return true;
		}
	}

	public static void main(String[] args) throws Exception {
		int limit = 100_000;
		File file = new File(System.getProperty("maps") != null ? System.getProperty("maps") : ".", "osm_gpx_data.csv.gz");

		if (!file.exists()) {
			System.err.println("File not found: " + file.getAbsolutePath());
			return;
		}

		TrackFilter filter = new TrackFilter();
		// filter.allowedActivities = Set.of("nospeed", "cycling");
		// filter.maxSpeed = 10.0;
		// filter.tagContains = "amenity";

		HashSkipTileQuadTree<GpxTrackObject> tracksTree = new HashSkipTileQuadTree<>();
		System.out.printf("=== 1. Reading max %d tracks from %s ===\n", limit, file.getName());
		
		long startCsv = System.currentTimeMillis();
		int loadedTracksCount = loadGpxTracks(file, tracksTree, limit, filter);
		long tracksBuildStart = System.nanoTime();
		tracksTree.build();
		long tracksBuildMs = (System.nanoTime() - tracksBuildStart) / 1_000_000;

		System.out.printf("Loaded %d tracks in %d ms (Tree build time: %d ms)\n\n", 
				loadedTracksCount, System.currentTimeMillis() - startCsv, tracksBuildMs);

		// 3. Дерево 2: Регионы OsmAnd
		HashSkipTileQuadTree<HSTQuadTreeJoinTest.RealMapObject> regionsTree = new HashSkipTileQuadTree<>();
		System.out.println("=== 2. Loading OsmAnd Regions ===");
		long startRegions = System.currentTimeMillis();
		
		OsmandRegions or = new OsmandRegions(null);
		Map<String, LinkedList<BinaryMapDataObject>> obj = or.cacheAllCountries();
		loadOsmandRegions(or, obj, regionsTree);

		long regionsBuildStart = System.nanoTime();
		regionsTree.build();
		long regionsBuildMs = (System.nanoTime() - regionsBuildStart) / 1_000_000;
		System.out.printf("Loaded OsmAnd Regions in %d ms (Tree build time: %d ms)\n\n", 
				System.currentTimeMillis() - startRegions, regionsBuildMs);

		// 4. Поиск пересечений (Spatial Join)
		System.out.println("=== 3. Executing Spatial Join (Tracks x Regions) ===");
		HashSkipTileQuadTreeJoiner<GpxTrackObject, HSTQuadTreeJoinTest.RealMapObject> joiner =
				new HashSkipTileQuadTreeJoiner<>(tracksTree, regionsTree);

		HashSkipTileQuadTree.SkipStats stats1 = new HashSkipTileQuadTree.SkipStats(tracksTree);
		HashSkipTileQuadTree.SkipStats stats2 = new HashSkipTileQuadTree.SkipStats(regionsTree);

		Map<String, Set<String>> regionToTracksMap = new HashMap<>();

		long joinStartNs = System.nanoTime();
		joiner.joinAllBuckets((trackEntry, regionEntry) -> {
			GpxTrackObject track = trackEntry.obj;
			HSTQuadTreeJoinTest.RealMapObject region = regionEntry.obj;

			String downloadName = region.downloadName;
			if (downloadName != null && !downloadName.isEmpty()) {
				regionToTracksMap.computeIfAbsent(downloadName, k -> new HashSet<>())
						.add(track.name + " (#" + track.id + ")");
			}
		}, stats1, stats2);

		long joinElapsedMs = (System.nanoTime() - joinStartNs) / 1_000_000;

		System.out.println("\n=========================================================================");
		System.out.println("                         BENCHMARK RESULTS                               ");
		System.out.println("=========================================================================");
		System.out.printf(" Total Tracks Processed  : %d\n", loadedTracksCount);
		System.out.printf(" Total Intersecting Regions: %d\n", regionToTracksMap.size());
		System.out.printf(" 🚀 Spatial Join Time     : %d ms\n", joinElapsedMs);
		System.out.println("-------------------------------------------------------------------------");
		System.out.println(" 📊 Skip Stats (Tracks Tree):");
		System.out.printf("    Total Skips Count   : %d\n", stats1.totalSkipsCount);
		System.out.printf("    Total Elements Skip : %d\n", stats1.totalElementsSkipped);
		System.out.println(" 📊 Skip Stats (Regions Tree):");
		System.out.printf("    Total Skips Count   : %d\n", stats2.totalSkipsCount);
		System.out.printf("    Total Elements Skip : %d\n", stats2.totalElementsSkipped);
		System.out.println("=========================================================================\n");

		List<Map.Entry<String, Set<String>>> sortedRegions = new ArrayList<>(regionToTracksMap.entrySet());
		sortedRegions.sort((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()));

		System.out.println("=== TOP REGIONS BY INTERSECTING GPX TRACKS ===");
		int topLimit = Math.min(15, sortedRegions.size());
		for (int i = 0; i < topLimit; i++) {
			Map.Entry<String, Set<String>> entry = sortedRegions.get(i);
			String regionName = entry.getKey();
			Set<String> tracks = entry.getValue();

			System.out.printf("\n%2d. Region: %-30s | Tracks Count: %d\n", i + 1, regionName, tracks.size());
			System.out.println("    Sample Tracks:");

			int sampleCount = 0;
			for (String trackInfo : tracks) {
				System.out.printf("      - %s\n", trackInfo);
				if (++sampleCount >= 3) {
					break;
				}
			}
		}
	}

	/**
	 */
	private static int loadGpxTracks(File gzFile, HashSkipTileQuadTree<GpxTrackObject> tree, int limit, TrackFilter filter) throws Exception {
		int count = 0;

		try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(gzFile));
			 BufferedReader reader = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {

			String headerLine = reader.readLine(); 
			if (headerLine == null) return 0;

			String line;
			int lineNum = 0;
			while ((line = reader.readLine()) != null && count < limit) {
				String[] cols = parseCsvLine(line);
				lineNum++;
				if (cols.length < 24) continue;

				try {
					long id = Long.parseLong(cols[0]);
					String name = cols[3];
					
					double minLat = Double.parseDouble(cols[9]);
					double minLon = Double.parseDouble(cols[10]);
					double maxLon = Double.parseDouble(cols[11]);
					double maxLat = Double.parseDouble(cols[12]);

					String tags = cols[13];
					String activity = cols[14];
					double speed = cols[15].isEmpty() ? 0.0 : Double.parseDouble(cols[15]);

					if (filter != null && !filter.test(activity, speed, tags)) {
						continue;
					}

					int min31X = MapUtils.get31TileNumberX(minLon);
					int max31X = MapUtils.get31TileNumberX(maxLon);
					int min31Y = MapUtils.get31TileNumberY(maxLat); // min Y maxLat
					int max31Y = MapUtils.get31TileNumberY(minLat); // max Y minLat

					int[] bbox31 = new int[] {
							Math.min(min31X, max31X),
							Math.min(min31Y, max31Y),
							Math.max(min31X, max31X),
							Math.max(min31Y, max31Y)
					};

					GpxTrackObject trackObj = new GpxTrackObject(id, name, activity, speed, tags, bbox31);
					tree.addObject(trackObj, bbox31, id);
					count++;
				} catch (Exception e) {
//					System.out.println("ERROR : " + lineNum + " - " + line);
				}
			}
		}
		return count;
	}

	private static void loadOsmandRegions(OsmandRegions or, Map<String, LinkedList<BinaryMapDataObject>> objMap,
										  HashSkipTileQuadTree<HSTQuadTreeJoinTest.RealMapObject> regionsTree) {
		long objectIdCounter = 0;

		for (Map.Entry<String, LinkedList<BinaryMapDataObject>> entry : objMap.entrySet()) {
			String regionName = entry.getKey();
			LinkedList<BinaryMapDataObject> list = entry.getValue();
			if (list == null || regionName == null) continue;

			for (BinaryMapDataObject o : list) {
				int[] coordinates = o.getCoordinates();
				if (coordinates == null || coordinates.length < 2) continue;

				int minX = Integer.MAX_VALUE;
				int minY = Integer.MAX_VALUE;
				int maxX = Integer.MIN_VALUE;
				int maxY = Integer.MIN_VALUE;

				for (int i = 0; i < coordinates.length; i += 2) {
					int x = coordinates[i];
					int y = coordinates[i + 1];
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}

				if (minX > maxX || minY > maxY) continue;

				int[] bbox = new int[] { minX, minY, maxX, maxY };
				long currentId = ++objectIdCounter;
				String dwName = or.getDownloadName(o);

				HSTQuadTreeJoinTest.RealMapObject realObj = new HSTQuadTreeJoinTest.RealMapObject(currentId, regionName, dwName, o, bbox);
				regionsTree.addObject(realObj, bbox, currentId);
			}
		}
	}

	private static String[] parseCsvLine(String line) {
		List<String> result = new ArrayList<>();
		boolean inQuotes = false;
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				inQuotes = !inQuotes;
			} else if (c == ',' && !inQuotes) {
				result.add(sb.toString().trim());
				sb.setLength(0);
			} else {
				sb.append(c);
			}
		}
		result.add(sb.toString().trim());
		return result.toArray(new String[0]);
	}
}