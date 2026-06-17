package net.osmand.search.core.spatial;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;
import net.osmand.search.core.HashQuadTree;
import net.osmand.search.core.spatial.SpatialSearchToken.NameIndexAtom;

public class SpatialSearchResultsList implements Comparable<SpatialSearchResultsList> {
	final SpatialSearchToken[] tokens; // non modifieable!
	final int tCount;
	

	// NameIndexAtom[][] -- should be double array to store list of combinations
	List<NameIndexAtom> linearResults = new ArrayList<>();
	TLongArrayList tileIds = new TLongArrayList();
	TIntArrayList tileZooms = new TIntArrayList();
	List<SpatialSearchResult> finalResult = null;
	HashQuadTree<Integer> quadTree = new HashQuadTree<>(16);

	public SpatialSearchResultsList() {
		this(null, null);
	}

	public SpatialSearchResultsList(SpatialSearchToken token, SpatialSearchResultsList parent) {
		if (token == null) {
			tokens = new SpatialSearchToken[0];
		} else {
			tokens = new SpatialSearchToken[parent.tokens.length + 1];
			tokens[0] = token;
			for (int k = 0; k < parent.tokens.length; k++) {
				tokens[k + 1] = parent.tokens[k];
			}
		}
		tCount = tokens.length;
	}
	
	public void loadObjects(SpatialSearchContext ctx) throws IOException {
		for (NameIndexAtom a : linearResults) {
			if (a.type == SpatialSearchToken.POI_TYPE) {
				a.object = ctx.readPoiObject(a.id);
			} else {
				a.object = ctx.readAddrObject(a.id, a.parentid);
			}
		}
		
	}

	public NameIndexAtom getAtom(int combination, int pos) {
		if (finalResult != null) {
			combination = finalResult.get(combination).parentInd;
		}
		return linearResults.get(combination * tokens.length + pos);
	}
	
	public List<SpatialSearchResult> getResult() {
		return finalResult;
	}

	public List<NameIndexAtom> getAtoms(int combination) {
		if (finalResult != null) {
			combination = finalResult.get(combination).parentInd;
		}
		int st = combination * tCount;
		return linearResults.subList(st, st + tCount);
	}

	public SpatialSearchToken getFirstToken() {
		return tokens.length == 0 ? null : tokens[0];
	}

	public int getCombinations() {
		return tileIds.size();
	}

	public int getTokenCount() {
		return tCount;
	}

	public List<SpatialSearchResult> sortResults(boolean deduplicate) {
		finalResult = new ArrayList<>(tileIds.size());
		for (int i = 0; i < tileIds.size(); i++) {
			finalResult.add(new SpatialSearchResult(this, i));
		}		
		Collections.sort(finalResult);
		if (deduplicate) {
			List<SpatialSearchResult> res = new ArrayList<SpatialSearchResult>();
			TLongHashSet duplicateIds = new TLongHashSet();
			for (SpatialSearchResult s : finalResult) {
				long filterId = s.getIdDeduplication();
				if (filterId > 0 && !duplicateIds.add(filterId)) {
					continue;
				}
				res.add(s);
			}
			finalResult = res;
		}
		return finalResult;
	}
	
	public void calculateIntersection(SpatialSearchToken token, SpatialSearchResultsList parent) {
		if (parent.getTokenCount() == 0) {
			addResult(null, 0, token.atoms);
		} else {
			// 1. iterate parent objects and find all objects from <parent>
			//    that are fully inside object <token> or have same the same tile 
			for (int i = 0; i < parent.tileIds.size(); i++) {
				long tileId = parent.tileIds.get(i);
				int zoom = parent.tileZooms.get(i);
				final int indx = i;
				token.quadTree.forEachMatch(zoom, tileId, t -> {
					addResult(parent, indx, t);
				});
			}
			// 2. reverse search quad tree from <token> and find objects
			//    that are fully inside any object <parent> and not the same the same tile!
			final SpatialSearchResultsList p = parent;
			for (final NameIndexAtom a : token.atoms) {
				parent.quadTree.forEachMatchHigherZoom(a.coords.bboxTileZoom, a.coords.bboxTileId, indxs -> {
					for (int indx : indxs) {
						addResult(p, indx, a);
					}
				});
			}
		}		
	}


	void addResult(SpatialSearchResultsList parent, int indx, List<NameIndexAtom> atoms) {
		for (NameIndexAtom a : atoms) {
			addResult(parent, indx, a);
		}
	}

	public int sumTokenAtomSize() {
		int sum = 0;
		for (SpatialSearchToken s : tokens) {
			sum += s.atoms.size();
		}
		return sum;
	}

	boolean addResult(SpatialSearchResultsList parent, int pindx, NameIndexAtom a) {
		boolean acceptIntersection = acceptIntersection(parent, pindx, a);
		if (!acceptIntersection) {
			return false;
		}
		finalResult = null;
		int pzoom = parent == null ? 0 : parent.tileZooms.get(pindx);
		int zoom = Math.max(pzoom, a.coords.bboxTileZoom);
		long tileId = pzoom > a.coords.bboxTileZoom ? parent.tileIds.get(pindx) : a.coords.bboxTileId;
		int insIndx = this.tileIds.size();
		this.linearResults.add(a);
		for (int i = 0; parent != null && i < parent.tCount; i++) {
			this.linearResults.add(parent.linearResults.get(pindx * parent.tCount + i));
		}

		this.tileIds.add(tileId);
		this.tileZooms.add(zoom);
		quadTree.put(zoom, tileId, insIndx);
		return true;
	}

	private boolean acceptIntersection(SpatialSearchResultsList parent, int pindx, NameIndexAtom a) {
		// no cache for now
		boolean intersect = true;
		for (int i = 0; parent != null && i < parent.tCount; i++) {
			NameIndexAtom pa = parent.linearResults.get(pindx * parent.tCount + i);
			if (!pa.coords.intersects(a.coords)) {
				intersect = false;
				break;
			}
		}
		if (!intersect) {
			return false;
		}
		
		// ignore multiple atomic objects intersections POI / Streets > 2!
		if (a.atomicObject()) {
			// check limit atomic objects to add
			List<Long> objects = new ArrayList<Long>(SpatialSearchContext.LIMIT_ATOMIC_OBJECTS);
			objects.add(a.id);
			for (int i = 0; parent != null && i < parent.tCount; i++) {
				NameIndexAtom pa = parent.linearResults.get(pindx * parent.tCount + i);
				if (pa.atomicObject()) {
					if (!objects.contains(pa.id)) {
						objects.add(pa.id);
					}
				}
				if (objects.size() > SpatialSearchContext.LIMIT_ATOMIC_OBJECTS) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public int compareTo(SpatialSearchResultsList o) {
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
		for (SpatialSearchToken t : tokens) {
			words.add(t.originalWord);
		}
		return String.format("Results list %d matched %s - %d (%d unique) results: %s", tCount, words,
				getCombinations(),
				finalResult == null ? -1 : finalResult.size(),
				extended ? linearResults : Collections.EMPTY_LIST);
	}
	
	

	@Override
	public String toString() {
		return toString(false);
	}

	
}