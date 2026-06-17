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
import net.osmand.binary.CommonWords;
import net.osmand.binary.NameIndexReader;
import net.osmand.map.OsmandRegions;
import net.osmand.util.SearchAlgorithms;




// GENERATION !

// TODO index street longer - Street Бульварно-Кудрявська вулиця(775) 15 19160 11048
// TODO same street in multiple city (assign same id?) - https://www.openstreetmap.org/way/74728182
// TODO "2-га Нова вулиця" - split by "-"?

// TODO Check file sizes??
////////////////////////
// BBOX EEFFICIENCY
// TODO !!! implement for tokens READ_COMMON_WORDS = false;

// TODO merge boundaries bbox - extend incomplete boundary same id...
// TODO Load objects by groups file order efficiently!
// TODO Ignore same embedded boundary city / county - deduplicate on the fly
// TODO sort tokens by actual frequency (do not use common words)

// TODO don't compute all combinations...
// TODO don't read objects while preparing tokens ? id duplicate between maps?
// TODO in the end recheck bbox boundary after load coordinates 31 (not 15)

// FEATURES
// TODO read buildings
// TODO duplicate words in query
// TODO COLLATOR + Last dot [CONSTANT] as incomplete, 
//      [NameIndexReader, SpatialSearchToken] 


// CACHE
// TODO Cache POI block read, cache city index !
// TODO Evict - NameIndexReader in caches ( > 200 - indexByRef, matchedKeys) full clear

// POI CATEGORIES 
// TODO Read all top poi categories for files
// TODO implement categories
// TODO World basemap

// SPECIAL CASES
// TODO Abbreviations Phase
// TODO Street intersection match
// TODO Sugggestion-correction
// TODO Progress / cancel

//////////////SEARCH ALGORITHM //////////////
// 1. Split words
// 2. Reinit caches
// 3. Sort words & meta information for words
// 3.1 Calculate poi categories for words & combinations!
// 3.2 Calculate common & frequent numbers based on files
// 3.3 Assign Common & frequent labels from Global file

// 4. Read 
// 4.1 Incomplete words? assign prefix ?
// 4.2 Read Per word List<AddressNameIndexDataAtom>, List<OsmAndPoiNameIndexDataAtom>
// 4.3 Read & Cache for non-frequent common words

// After search operations - Expand POI Type filters for results
// 5.1 Run rare words (by counts & labels)
// 5.2 Run with frequent words
// 5.3 Expand POI categories

// Search categories
// MAPS ITERATION 1 - close up, 2 - larger radius 
// Phase I - only rare + words replaced abbreviations
// Phase II - all words + replaced abbreviations
// Phase III - all words + replaced abbreviations

public class SpatialTextSearch {
	
	private static final int LIMIT_PRINT = 100;
	public static boolean DEDUPLICATE_RES = true;
	
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

	private List<SpatialSearchToken> splitWords(String input) {
		List<String> owords =new ArrayList<String>();
		List<String> words = SearchAlgorithms.splitAndNormalizeSearchQuery(input, owords);
		List<SpatialSearchToken> tokens = new ArrayList<>();
		for (int order = 0; order < words.size(); order++) {
			String w = words.get(order);
			tokens.add(new SpatialSearchToken(w, owords.get(order), order));
		}
		return tokens;
	}
	
	private void sortTokens(List<SpatialSearchToken> tokens) {
		// TODO donot use common words class
		Collections.sort(tokens, new Comparator<SpatialSearchToken>() {
			@Override
			public int compare(SpatialSearchToken o1, SpatialSearchToken o2) {
				int c1 = CommonWords.getCommonSearch(o1.word);
				int c2 = CommonWords.getCommonSearch(o2.word);
				if(c1 != c2) {
					return Integer.compare(c1, c2);
				}
				c1 = o1.atoms.size();
				c2 = o2.atoms.size();
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
			res.mainResult.sortResults(DEDUPLICATE_RES);
		}
		return res;
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
		System.out.println("All Results: ");
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
		DEDUPLICATE_RES = true;
		File folder = new File("/Users/victorshcherb/osmand/maps/");
		String pattern = "Germany_b";
		String query = "Berlin hauptstrasse"; // slow 
		query = "Kelterstraße Kernen im Remstal";
		query = "Germany Kelter. Kernen im Remstal";
		
		pattern = "Us_";
		query = "Salt Lake City Pennsylvania Place";
//		query = "Salt Lake City Pennsylvania Street";
		query = "Salt Lake City";
		
//		pattern = "Liechtenstein_europe.obf";
//		query = "Vaduz Lettstrasse";
//		query = "Vaduz ";
//		query = "Jugendheim Malbun";
		
//		query = "USA Salt Lake City Pennsylvania Street 41";
		
		pattern = "Ukraine_";
		query = "нова пошта Бульварно кудрявс.";
//		query = "kyiv Бульварно кудряв.";
		// TODO Бульварно-Кудрявська (not searching), 2-га? (searching?)
		// Catedral-Basílica de Nuestra Señora del Pilar
		// TODO №59 (366443448) missing?
//		query = "Ukraine kyiv saks.";
//		query = "Ukraine kyiv";
//		query = "пузата хата mcdonal.";
//		query = "Нова пошта 53";
		
//		pattern = "Spain_aragon_europe_";
//		query = "Basílica de Nuestra Señora del Pilar";
		
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