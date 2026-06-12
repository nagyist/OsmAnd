package net.osmand.search.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.util.SearchAlgorithms;

public class SearchManyWordsAlgorithm {
	
	
	// todo duplicate words
	private static class SearchToken {
		int order = 0;
		boolean incomplete;
		String originalWord;
		String normalized;
		boolean common;
		
		List<TokenIndexCache> byFiles = new ArrayList<>();
		
		public boolean isCommon() {
			return common;
		}
		
		public int matched() {
			// todo
			return 0;
		}
		
		public SearchToken(String w, int order) {
			originalWord = w;
			normalized = w;
			this.order = order;
		}
		
	}
	
	private static class SearchGlobalCacheContext {
		
	}
	
	private static class TokenIndexCache {
		private int frequency;
		private int matched;
	}
	
	private static class NameIndexFile {
		BinaryMapIndexReader reader;
		boolean poi;
		long filePointer;
		Map<String, TokenIndexCache> commonWords = new HashMap<>();
		Map<String, TokenIndexCache> cachedWords = new HashMap<>();

	}
	
	// special cases
	// 1. Abbrevations
	// 2. Street intersection match
	
	public void search(String input, List<BinaryMapIndexReader> files) {
		// TODO dot shouldn't split
		List<SearchToken> tokens = splitWords(input);
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
		
		// After search operations
		// Expand POI Type filters for results
		
		Collections.sort(tokens, new Comparator<>() {

			@Override
			public int compare(SearchToken o1, SearchToken o2) {
				// TODO Auto-generated method stub
				return 0;
			}
		});
		
		for (BinaryMapIndexReader r : files) {

		}
	}

	private List<SearchToken> splitWords(String input) {
		List<String> words = SearchAlgorithms.splitAndNormalize(input);
		List<SearchToken> tokens = new ArrayList<>();
		int order = 0;
		for (String w : words) {
			tokens.add(new SearchToken(w, order));
		}
		return tokens;
	}
	
	
	
	
}
