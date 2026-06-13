package net.osmand.search.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;
import net.osmand.search.core.SearchManyWordsAlgorithm.NameIndexAtom;
import net.osmand.search.core.SearchManyWordsAlgorithm.SearchToken;

public class SearchManyResultsList implements Comparable<SearchManyResultsList> {
	private final SearchToken[] tokens; // non modifieable!
	private final int tCount;

	// NameIndexAtom[][] -- should be double array to store list of combinations
	List<NameIndexAtom> linearResults = new ArrayList<>();
	TLongArrayList tileIds = new TLongArrayList();
	TIntArrayList tileZooms = new TIntArrayList();
	List<OrderResult> order = null;
	HashQuadTree<Integer> quadTree = new HashQuadTree<>(16);

	public SearchManyResultsList() {
		this(null, null);
	}

	public SearchManyResultsList(SearchToken token, SearchManyResultsList parent) {
		if (token == null) {
			tokens = new SearchToken[0];
		} else {
			tokens = new SearchToken[parent.tokens.length + 1];
			tokens[0] = token;
			for (int k = 0; k < parent.tokens.length; k++) {
				tokens[k + 1] = parent.tokens[k];
			}
		}
		tCount = tokens.length;
	}

	public NameIndexAtom getAtom(int combination, int pos) {
		if (order != null) {
			combination = order.get(combination).index;
		}
		return linearResults.get(combination * tokens.length + pos);
	}

	public List<NameIndexAtom> getAtoms(int combination) {
		if (order != null) {
			combination = order.get(combination).index;
		}
		int st = combination * tCount;
		return linearResults.subList(st, st + tCount);
	}

	public SearchToken getFirstToken() {
		return tokens.length == 0 ? null : tokens[0];
	}

	public int getCombinations() {
		return tileIds.size();
	}

	public int getTokenCount() {
		return tCount;
	}

	public void sortResults() {
		order = new ArrayList<>(tileIds.size());
		for (int i = 0; i < tileIds.size(); i++) {
			order.add(new OrderResult(i));
		}

		Collections.sort(order, new Comparator<OrderResult>() {

			@Override
			public int compare(OrderResult o1, OrderResult o2) {
				int res = Integer.compare(o1.uniqObjects(), o2.uniqObjects());
				if (res != 0) {
					return res;
				}
				return -Integer.compare(o1.index, o2.index);
			}
		});

	}

	void addResult(List<NameIndexAtom> atoms) {
		addResult(null, 0, atoms);
	}

	void addResult(SearchManyResultsList parent, int indx, List<NameIndexAtom> atoms) {
		for (NameIndexAtom a : atoms) {
			addResult(parent, indx, a);
		}
	}

	public int sumTokenAtomSize() {
		int sum = 0;
		for (SearchToken s : tokens) {
			sum += s.atoms.size();
		}
		return sum;
	}

	void addResult(SearchManyResultsList parent, int pindx, NameIndexAtom a) {
		order = null;
		int pzoom = parent == null ? 0 : parent.tileZooms.get(pindx);
		int zoom = Math.max(pzoom, a.bboxTileZoom);
		long tileId = pzoom > a.bboxTileZoom ? parent.tileIds.get(pindx) : a.bboxTileId;
		int insIndx = this.tileIds.size();
		// not many duplicates
//			boolean dup = checkDuplicate(parent, pindx, a, zoom, tileId);
//			if (dup) return;
		this.linearResults.add(a);
		for (int i = 0; parent != null && i < parent.tCount; i++) {
			this.linearResults.add(parent.linearResults.get(pindx * parent.tCount + i));
		}

		this.tileIds.add(tileId);
		this.tileZooms.add(zoom);
		quadTree.put(zoom, tileId, insIndx);
	}

	boolean checkDuplicate(SearchManyResultsList parent, int pindx, NameIndexAtom a, int zoom, long tileId) {
		final boolean[] matched = new boolean[1];
		quadTree.forEachMatch(zoom, zoom, tileId, indxs -> {
			for (int indx : indxs) {
				boolean m = linearResults.get((indx + 1) * tCount - 1).id == a.id;
				for (int i = 0; m && parent != null && i < parent.tCount; i++) {
					NameIndexAtom p = parent.linearResults.get(pindx * parent.tCount + i);
					NameIndexAtom p2 = linearResults.get(indx * tCount + i);
					if (p.id != p2.id) {
						m = false;
					}
				}
				matched[0] |= m;
			}
		});
		return matched[0];
	}

	private class OrderResult {
		int index;
		int uniq = -1;

		OrderResult(int o) {
			this.index = o;
		}

		public int uniqObjects() {
			if (uniq != -1) {
				return uniq;
			}
			// TODO not exactly uniq multiple files?
			TLongHashSet set = new TLongHashSet();
			for (int k = 0; k < tCount; k++) {
				set.add(linearResults.get(index * tCount + k).id);
			}
			uniq = set.size();
			return uniq;
		}
	}

	@Override
	public int compareTo(SearchManyResultsList o) {
		int s1 = tCount;
		int s2 = o.tCount;
		if (s1 != s2) {
			return -Integer.compare(s1, s2);
		}
		s1 = sumTokenAtomSize();
		s2 = o.sumTokenAtomSize();
		return Integer.compare(s1, s2);
	}

	public String toString(boolean extended) {
		List<String> words = new ArrayList<>();
		for (SearchToken t : tokens) {
			words.add(t.originalWord);
		}
		return String.format("Results list %d matched %s - %d results: %s", tCount, words, getCombinations(),
				extended ? linearResults : Collections.EMPTY_LIST);
	}

	@Override
	public String toString() {
		return toString(false);
	}
}