package net.osmand.search.core.spatial;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.osmand.binary.BinaryMapAddressReaderAdapter.AddressRegion;
import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.binary.BinaryMapPoiReaderAdapter.PoiRegion;
import net.osmand.binary.NameIndexReader;
import net.osmand.map.OsmandRegions;
import net.osmand.util.SearchAlgorithms;



//////////// OTHER TASKS ///////
// Check file sizes: 
// 1. REVIEW ADD_TOP_X_FREQ_WORDS (many common?)
// 2. REVIEW added bbox31 size
// 3. REVIEW if POI / Address is searched correctly - split Words - splitAndNormalizeSearchQuery(SearchPhrase.ALLDELIMITERS_WITH_HYPHEN);
//    - 2-га Нова (2 Нова), Бульварно-Кудрявська
// 4. TEST / REVIEW duplicate words in query Pennsylvania Street in Pennsylvania
// 5. English postcodes
// 6. TEST / REVIEW - COLLATOR + Last dot [CONSTANT] as incomplete, [NameIndexReader, SpatialSearchToken]


// DONE TEST
// Load objects by groups file order efficiently!
// Index street longer - Street Бульварно-Кудрявська вулиця(775) 15 19160 11048 bytes[2] >= 1
// "2-га Нова вулиця" - split by "-"?
// !!! implement for tokens READ_COMMON_WORDS = false; Нова вулиця very slow!
// Same street in multiple city (assign same id?) - https://www.openstreetmap.org/way/74728182 - TODO

/////////////////////////////////
// TODO [[2, нова, вулиця] STREET_TYPE 2-га Нова вулиця (-2626) 50.5006 30.3798 ]
//  to search buildings most complete street is needed (largest city sort?)

// TODO don't compute all combinations... (!) and do it in the right order 2^7
// TODO Ignore same embedded boundary city / county - deduplicate on the fly

// FEATURES
// TODO Search Buildings
// TODO Read all top poi categories for files
// TODO POI Categories implement categories
// TODO World basemap ! POI  
// TODO Street intersection match
// TODO Progress / cancel
// TODO Abbreviations Phase
// TODO Sugggestion-correction
// TODO Combine by wikidata id ?

// ISSUES
// TODO test: merge boundaries bbox - extend incomplete boundary same id...
// TODO ? test: duplicate words in query
// TODO ? review settings: don't read objects while preparing tokens ? id duplicate between maps?
// TODO ? in the end recheck bbox boundary after load coordinates 31 (not 15)

// CACHE
// TODO Evict - NameIndexReader in caches ( > 200 - indexByRef, matchedKeys) full clear
// TODO Cache Loaded objects ?

//////////////// SEARCH ALGORITHM /////////////////
// 1. Init files + read caches
// 2. Split tokens
// 3. Read tokens -> atoms (
// 4. Sort tokens to do combinations
// 5. Find combinations
// 6. Sort results, filter results
// 7. Expand poi categories if needed

////////////// TODO THINK OPTIMIZATIONS /////////////
// 1. PARTIAL SEARCH. Perform equals search and then with '.'
// 2. MAPS. Do search first with closest maps and then with others
// 3. ALL COMBINATIONS. Stop on one combination or find all
// 4. POI CATEGORIES. ? 
// 5. READ_ALL. Switch ALWAYS_READ_COMMON_WORDS_ATOMS=true 
//    It couldn't give any new complete result but could give partial results
public class SpatialTextSearch {
	
	private static final int LIMIT_PRINT = 300;
	
	public static class SpatialTextSearchSettings {
		
		public static boolean SEARCH_ADDR = true;
		public static boolean SEARCH_POI = true;

		// Deduplicate results in the end by checking osm id of the first object in combination
		public static boolean DEDUPLICATE_RES = true;

		// READ OBJECTS before intersection to reduce number of duplicates from different maps by osm id
		// - needs to be tested performance mostly slows down
		public static boolean READ_ADDR_OBJECTS = false;
		public static boolean READ_POI_OBJECTS = false; 
		
		// no need to find 3 street intersection or 3 POI intersection
		public static int LIMIT_ATOMIC_OBJECTS = 2;
		
		// Performance improvement assuming for rare words we don't read common atoms 
		public static boolean ALWAYS_READ_COMMON_WORDS_ATOMS = false;
		public static boolean ALWAYS_READ_FREQ_WORDS_ATOMS = true;

	}
	
	public static class SpatialSearchFileCache {
		public int fileInd = -1; // changing each session - not concurrent !!!
		public int indexInd = -1; // changing each session - not concurrent !!!
		public final String file;
		public final long length;
		public final long edition;
		public final List<NameIndexReader> indexReaders = new ArrayList<NameIndexReader>();
		
		public SpatialSearchFileCache(BinaryMapIndexReader r) {
			file = r.getFile().getName();
			length = r.getFile().length();
			edition = r.getDateCreated();
			for (AddressRegion a : r.getAddressIndexes()) {
				indexReaders.add(new NameIndexReader(a));
			}
			for (PoiRegion a : r.getPoiIndexes()) {
				indexReaders.add(new NameIndexReader(a));
			}
		}
		
		public boolean test(BinaryMapIndexReader r) {
			return r.getFile().getName().equals(file) && r.getFile().length() == length && 
					r.getDateCreated() == edition;
		}
	}
	
	
	public static class SpatialSearchGlobalCache {
		
		public Map<String, SpatialSearchFileCache> filesCache = new HashMap<>();
		
	}
	
	public static class SpatialSearchResults {
		
		public String input;
		
		public List<SpatialSearchToken> tokens;
		
		public SpatialSearchResultsList mainResult;
		
		public List<SpatialSearchResultsList> combinations;
	}
	
	SpatialSearchGlobalCache cache = new SpatialSearchGlobalCache(); // reusable between sessions
	
	private void sortTokens(List<SpatialSearchToken> tokens) {
		// sort from least atoms to do combinations as the most efficient
		Collections.sort(tokens, new Comparator<SpatialSearchToken>() {
			@Override
			public int compare(SpatialSearchToken o1, SpatialSearchToken o2) {
				int c1 = o1.atoms.size();
				int c2 = o2.atoms.size();
				if(c1 != c2) {
					return Integer.compare(c1, c2);
				}
				return o1.word.compareTo(o2.word);
			}
			
		});
		for(int i = 0; i < tokens.size(); i++) {
			tokens.get(i).sortedOrder = i;
		}
	}
	

	private List<SpatialSearchResultsList> findObjCombinations(List<SpatialSearchToken> tokens) {
		LinkedList<SpatialSearchResultsList> candidates = new LinkedList<>();
		candidates.add(new SpatialSearchResultsList());
		List<SpatialSearchResultsList> result = new ArrayList<>();
//		System.out.println("TOKENS " + tokens);
		while (!candidates.isEmpty()) {
			SpatialSearchResultsList parent = candidates.removeFirst();
			if (parent.getCombinations() > 0) {
				result.add(parent);
			}
			for (SpatialSearchToken token : tokens) {
				if (parent.getTokenCount() == 0 || token.sortedOrder < parent.getFirstToken().sortedOrder) {
//					System.out.println("ITERATION Token [ " + token + " ] + " + parent);
					SpatialSearchResultsList next = new SpatialSearchResultsList(token, parent);
					next.calculateIntersection(token, parent);
					candidates.add(next);
				}
			}
		}
		return result;
		
	}
	
	public SpatialSearchResults searchAPI(String input, SpatialSearchContext ctx) throws IOException {
		SpatialSearchResults res = new SpatialSearchResults();
		ctx.initFiles(cache);
		res.input = input;
		// 1. prepare tokens
		res.tokens = splitWords(input);

		// 2. read atoms
		ctx.stats.atoms -= System.nanoTime();
		ctx.readAtoms(res.tokens);
		ctx.stats.atoms += System.nanoTime();
		
		// 3. sort tokens 
		sortTokens(res.tokens);
		
		// 4. find combinations
		ctx.stats.computeTime -= System.nanoTime();
		res.combinations = findObjCombinations(res.tokens);
		ctx.stats.computeTime += System.nanoTime();
		// 5. sort combinations, load objects, objects and filter duplicate
		Collections.sort(res.combinations);
		if (res.combinations.size() > 0) {
			res.mainResult = res.combinations.get(0);
			ctx.stats.atoms -= System.nanoTime();
			res.mainResult.loadObjects(ctx);
			ctx.stats.atoms += System.nanoTime();
			res.mainResult.sortResults(SpatialTextSearchSettings.DEDUPLICATE_RES);
		}
		return res;
	}

	public List<SpatialSearchToken> splitWords(String input) {
		List<String> owords = new ArrayList<String>();
		// split by hyphen as we supposed to index them separately
		List<String> words = SearchAlgorithms.splitAndNormalize(input, owords);
		List<SpatialSearchToken> tokens = new ArrayList<>();
		for (int order = 0; order < words.size(); order++) {
			String w = words.get(order);
			SpatialSearchToken token = new SpatialSearchToken(w, owords.get(order), order);
			tokens.add(token);
		}
		return tokens;
	}

	public void searchTest(String input, SpatialSearchContext ctx) throws IOException {
		SpatialSearchResults res = searchAPI(input, ctx);
		ctx.stats.finish();
		if (res.mainResult != null) {
			System.out.println("--------");
			System.out.println("Main: " + res.mainResult);
			int limit = LIMIT_PRINT;
			int all = res.mainResult.getCombinations();
			for (SpatialSearchResult r : res.mainResult.getResult()) {
				if (limit-- < 0) {
					System.out.println(".............");
					break;
				}
				System.out.println(r);
			}
			int unique = res.mainResult.sortResults(true).size();
			System.out.printf("------ ALL %d results, unique %d ------- \n ", all, unique);
			System.out.println("---------------------------------------");
		}
		
		System.out.println("\nTokens: " + res.tokens);
		System.out.printf("All Combinations - %d: \n", res.combinations.size());
		for (SpatialSearchResultsList s : res.combinations) {
			if (s.getTokenCount() >= 2) {
				s.sortResults(true);
				System.out.println("  " + s.toString(false));
//				int limit = LIMIT_PRINT;
//				for (SpatialSearchResult r : s.getResult()) {
//					if (limit-- < 0) {
//						System.out.println(".............");
//						break;
//					}
//					System.out.println(r);
//				}
			}
		}
		
		
		System.out.println(ctx.stats);
		System.out.println();
	}

	
	
	

	private static void initFile(List<BinaryMapIndexReader> ls, File f) throws IOException, FileNotFoundException {
		if (f.exists() && (f.getName().endsWith(".obf") || f.getName().equals(OsmandRegions.REGIONS_OCBF))) {
			BinaryMapIndexReader bir = new BinaryMapIndexReader(new RandomAccessFile(f, "r"), f);
			ls.add(bir);
		}
	}
	
	public static void main(String[] args) throws IOException, InterruptedException {
		SpatialTextSearchSettings.DEDUPLICATE_RES = true;
		File folder = new File("/Users/victorshcherb/osmand/maps/");
		String pattern = "Germany_b";
		String query = "Berlin hauptstrasse"; // slow 
		query = "Kelterstraße Kernen im Remstal";
		query = "Germany Kelter. Kernen im Remstal";
		
		pattern = "Us_";
		query = "Salt Lake City Pennsylvania Place";
		query = "Salt Lake City Pennsylvania Street";
//		query = "Salt Lake City";
		
//		pattern = "Liechtenstein_europe.obf";
//		query = "Vaduz Lettstrasse";
//		query = "Vaduz ";
//		query = "Jugendheim Malbun";
		
//		query = "USA Salt Lake City Pennsylvania Street 41";
		
		pattern = "Ukraine_kyiv";
		pattern = "Map";
//		query = "нова пошта Бульварно Кудрявська";
//		query = "Бульварно-кудрявс.";
//		query = "2 Нова вулиця"; 
//		query = "Ukraine kyiv saks.";
//		query = "Ukraine Київ";
//		query = "пузата хата mcdonal.";
//		query = "Нова пошта 53";
		query = "2 Нова вулиця";
		
//		pattern = "Spain_aragon_europe_";
//		query = "Basílica de Nuestra Señora del Pilar";
//		query = "Catedral-Basílica de Nuestra Señora del Pilar"; // 7 words! 2^7 combinations
		
		long t = System.nanoTime();
		
		List<BinaryMapIndexReader> ls = new ArrayList<BinaryMapIndexReader>();
		for (File f : folder.listFiles()) {
			if (f.getName().startsWith(pattern) || f.getName().equals(OsmandRegions.REGIONS_OCBF)) {
				initFile(ls, f);
			}
		}
		SpatialTextSearch a = new SpatialTextSearch();
		System.out.println(String.format("Index files %.1f ms", (System.nanoTime() - t) / 1e6));
		
		
		SpatialSearchContext searchContext = new SpatialSearchContext(ls);
		a.searchTest(query, searchContext);
		
		searchContext = new SpatialSearchContext(ls);
		a.searchTest(query, searchContext);
	}
	
	public static void mainTest(String[] subArgsArray) throws FileNotFoundException, IOException {
		long t = System.nanoTime();
		String query = subArgsArray[0];
		List<BinaryMapIndexReader> ls = new ArrayList<BinaryMapIndexReader>();
		for(int i = 1; i < subArgsArray.length; i++) {
			File fl = new File(subArgsArray[i]);
			if (fl.isFile()) {
				if (i == 1) {
					initFile(ls, new File(fl.getParentFile(), OsmandRegions.REGIONS_OCBF));
				}
				initFile(ls, fl);
			} else {
				for (File f : fl.listFiles()) {
					initFile(ls, f);
				}
			}
		}
		System.out.println(String.format("Index files %.1f ms", (System.nanoTime() - t) / 1e6));
		SpatialTextSearch a = new SpatialTextSearch();
		SpatialSearchContext searchContext = new SpatialSearchContext(ls);
		a.searchTest(query, searchContext);
	}

	
}