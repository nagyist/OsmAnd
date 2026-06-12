package net.osmand.search.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.hash.TLongObjectHashMap;
import net.osmand.binary.BinaryMapAddressReaderAdapter.AddressRegion;
import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.binary.BinaryMapPoiReaderAdapter.PoiRegion;
import net.osmand.binary.CommonWords;
import net.osmand.binary.NameIndexInspector;
import net.osmand.binary.NameIndexInspector.PrefixNameValue;
import net.osmand.binary.OsmandOdb.AddressNameIndexDataAtom;
import net.osmand.binary.OsmandOdb.OsmAndPoiNameIndexDataAtom;
import net.osmand.util.SearchAlgorithms;

// TODO duplicate words
// TODO replace last dot as incomplete
// TODO sort tokens by actual frequency
// special cases
// 1. Abbrevations
// 2. Street intersection match
public class SearchManyWordsAlgorithm {
	
	
	
	record NameIndexAtom(String name, AddressNameIndexDataAtom addr, OsmAndPoiNameIndexDataAtom poi, BinaryMapIndexReader file) {
	};
	

	static class SearchManyStats {
		long time = System.nanoTime();
		long readTime = 0;
		
		@Override
		public String toString() {
			return String.format("Search Stats %.1f ms - read %.1f ms", time / 1e6, readTime / 1e6);
		}
		
		public void finish() {
			time = System.nanoTime() - time;
		}
	}
	
	static class SearchManyContext {
		List<BinaryMapIndexReader> files;
		SearchManyStats stats = new SearchManyStats();
		
	}
	
	static class SearchTokenCombination {
		List<SearchToken> tokens = new ArrayList<>();
		TLongObjectHashMap<NameIndexAtom> index = new TLongObjectHashMap<>();
		
	}
	
	static class SearchToken {
		int originalOrder = 0;
		int sortedOrder = 0;
		boolean incomplete;
		String originalWord;
		String word;
		List<NameIndexAtom> atoms = new ArrayList<>();
		TLongObjectHashMap<NameIndexAtom> index = new TLongObjectHashMap<>();
		
		public SearchToken(String w, int order) {
			originalWord = w;
			word = w;
			this.originalOrder = order;
		}
		
		@Override
		public String toString() {
			return String.format("%d. %s - %d atoms", originalOrder, originalWord, atoms.size());
		}
		
		private long makeId(int fileInd,long shiftToIndex) {
			int SHIFT = 12;
			if (fileInd > 1 << SHIFT) {
				throw new IllegalStateException();
			}
			long id = (shiftToIndex << SHIFT) + SHIFT;
			return id;
		}

		// TODO properly calculate shiftToIndex
		public void addAtom(String name, BinaryMapIndexReader b, int fileInd, AddressNameIndexDataAtom a) {
			NameIndexAtom atom = new NameIndexAtom(name, a, null, b);
			addAtom(name, fileInd, a.getShiftToIndex(0), atom);
		}

		// TODO properly calculate shiftToIndex
		public void addAtom(String name, BinaryMapIndexReader b, int fileInd, OsmAndPoiNameIndexDataAtom a) {
			NameIndexAtom atom = new NameIndexAtom(name, null, a, b);
			addAtom(name, fileInd, a.getShiftTo(), atom);
		}
		
		private void addAtom(String name, int fileInd, long ref, NameIndexAtom atom) {
			if (match(name)) {
				long lid = makeId(fileInd, ref);
				if (index.containsKey(lid)) {
					throw new IllegalStateException();
				}
				index.put(lid, atom);
				atoms.add(atom);
			}
		}

		private boolean match(String name) {
			// TODO collator, dot
			return word.equalsIgnoreCase(name);
		}
	}
	
	public static void main(String[] args) throws IOException {
		File folder = new File("/Users/victorshcherb/osmand/maps/");
		String pattern = "Germany_baden";
		String query = "Berlin hauptstrasse";
		query = "Kelterstraße Kernen im Remstal";
		long t = System.nanoTime();
		List<BinaryMapIndexReader> ls = new ArrayList<BinaryMapIndexReader>();
		for (File f : folder.listFiles()) {
			if(f.getName().endsWith(".obf") && f.getName().startsWith(pattern)) {
				BinaryMapIndexReader bir = new BinaryMapIndexReader(new RandomAccessFile(f, "r"), f);
				ls.add(bir);
			}
		}
		System.out.println(String.format("Index files %.1f ms", (System.nanoTime() - t) / 1e6));
		SearchManyWordsAlgorithm a = new SearchManyWordsAlgorithm();
		a.searchTest(query, ls);
	}
	
	// TODO dot shouldn't split words
	private List<SearchToken> splitWords(String input) {
		List<String> words = SearchAlgorithms.splitAndNormalize(input);
		List<SearchToken> tokens = new ArrayList<>();
		int order = 0;
		for (String w : words) {
			tokens.add(new SearchToken(w, order++));
		}
		return tokens;
	}
	
	private void sortTokens(List<SearchToken> tokens) {
		Collections.sort(tokens, new Comparator<SearchToken>() {
			@Override
			public int compare(SearchToken o1, SearchToken o2) {
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
	

	private List<SearchTokenCombination> findSingleObjCombinations(List<SearchToken> tokens) {
		LinkedList<SearchTokenCombination> candidates = new LinkedList<>();
		candidates.add(new SearchTokenCombination());
		List<SearchTokenCombination> result = new ArrayList<>();
		while (!candidates.isEmpty()) {
			SearchTokenCombination parent = candidates.removeFirst();
			if (parent.index.size() > 0) {
				result.add(parent);
			}
			for (SearchToken t : tokens) {
				if (parent.tokens.isEmpty() || t.sortedOrder < parent.tokens.get(0).sortedOrder) {
					SearchTokenCombination next = new SearchTokenCombination();
					next.tokens.add(t);
					next.tokens.addAll(parent.tokens);
					if (parent.tokens.isEmpty()) {
						next.index = t.index;
					} else {
						TLongObjectIterator<NameIndexAtom> it = parent.index.iterator();
						while (it.hasNext()) {
							it.advance();
							long key = it.key();
							if (t.index.contains(key)) {
								next.index.put(key, it.value());
							}
						}
					}
					if (next.index.size() > 0) {
						candidates.add(next);
					}
				}
			}
		}
		return result;
		
	}
	
	public void searchTest(String input, List<BinaryMapIndexReader> files) throws IOException {
		SearchManyContext ctx = new SearchManyContext();
		ctx.files = files;
		// 1. prepare tokens
		List<SearchToken> tokens = splitWords(input);
		// 2. read atoms
		for (SearchToken t : tokens) {
			readAtoms(ctx, t);
		}
		// 3. sort tokens 
		sortTokens(tokens);
		// 4. find combinations
		List<SearchTokenCombination> singleCombinations = findSingleObjCombinations(tokens);
		
		for(SearchTokenCombination s: singleCombinations ) {
//			if()
		}
		
		
		ctx.stats.finish();
		System.out.println(tokens);
		System.out.println(ctx.stats);
	}

	


	private void readAtoms(SearchManyContext ctx, SearchToken t) throws IOException {
		int fileInd = 0;
		for (BinaryMapIndexReader b : ctx.files) {
			for (AddressRegion m : b.getAddressIndexes()) {
				ctx.stats.readTime -= System.nanoTime();
				NameIndexInspector indx = b.readFullNameIndex(m, t.word);
				ctx.stats.readTime += System.nanoTime();
				for (PrefixNameValue prefix : indx.getPrefixes()) {
					addAtoms(t, b, fileInd, prefix);
				}
			}
			for (PoiRegion m : b.getPoiIndexes()) {
				ctx.stats.readTime -= System.nanoTime();
				NameIndexInspector indx = b.readFullNameIndex(m, t.word);
				ctx.stats.readTime += System.nanoTime();
				for (PrefixNameValue prefix : indx.getPrefixes()) {
					addAtoms(t, b, fileInd, prefix);
				}
			}
			fileInd++;
		}
	}

	private void addAtoms(SearchToken t, BinaryMapIndexReader b, int fileInd, PrefixNameValue prefix) {
		int INT_BITS = 32;
		String curSuffix = null;
		List<String> suffixes = new ArrayList<>();
		boolean addr = prefix.addr != null;
		for (String s : addr ? prefix.addr.getSuffixesDictionaryList() : 
				prefix.poi.getSuffixesDictionaryList()) {
			curSuffix = SearchAlgorithms.nameIndexDecodeDictionarySuffix(curSuffix, s);
			suffixes.add(prefix.key + curSuffix);
		}
		if (addr) {
			for (AddressNameIndexDataAtom a : prefix.addr.getAtomList()) {
				for (int i = 0; i < a.getSuffixesBitsetCount(); i++) {
					int suffBit = a.getSuffixesBitset(i);
					for (int j = 0; j < INT_BITS && suffBit != 0; j++) {
						if (suffBit % 2 == 1) {
							int ind = i * INT_BITS + j;
							t.addAtom(suffixes.get(ind), b, fileInd, a);
						}
						suffBit >>>= 1;
					}
				}
			}
		} else {
			for (OsmAndPoiNameIndexDataAtom a : prefix.poi.getAtomsList()) {
				for (int i = 0; i < a.getSuffixesBitsetCount(); i++) {
					int suffBit = a.getSuffixesBitset(i);
					for (int j = 0; j < INT_BITS && suffBit != 0; j++) {
						if (suffBit % 2 == 1) {
							int ind = i * INT_BITS + j;
							t.addAtom(suffixes.get(ind), b, fileInd, a);
						}
						suffBit >>>= 1;
					}
				}
			}
		}
	}
	

	public void documentation(String input, List<BinaryMapIndexReader> files) {
		// 1. Split words
		// 2.1 Global cache Read common words for files
		// 2.2 Global cache Read all top poi categories for files
		
		// 3. Sort words & meta information for words
		// 3.1 Calculate poi categories for words & combinations!
		// 3.2 Calculate common & frequent numbers based on files
		// 3.3 Assign Common & frequent labels from Global file
		
		////////////// Iteration on 1st group Files? 100km ////////////
		// 4. Read 
		// 4.1 Incomplete words? assign prefix ?
		// 4.2 Read Per word List<AddressNameIndexDataAtom>, List<OsmAndPoiNameIndexDataAtom>
		// 4.3 Read & Cache for non-frequent common words
		
		////////////// SEARCH ALGORITHM //////////////
		// 5.1 Run rare words (by counts & labels)
		// 5.2 Run with frequent words
		// 5.3 Expand poi categories
		
		// Search categories
		// Phase I - only rare + words replaced abbrrevations
		// Phase II - all words + replaced abbrevations
		// Phase III - all words + replaced abbrevations
		
		// After search operations - Expand POI Type filters for results
		
	}
	
	
}
