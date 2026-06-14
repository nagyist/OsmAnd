package net.osmand.search.core.spatial;

import java.util.ArrayList;
import java.util.List;

import net.osmand.search.core.spatial.SpatialTextSearch.NameIndexAtom;
import net.osmand.search.core.spatial.SpatialTextSearch.SpatialSearchToken;

public class SpatialSearchResult implements Comparable<SpatialSearchResult> {

	final int parentInd;
	final SpatialSearchResultsList parent;
	final List<SpatialSearchResultRef> objs = new ArrayList<>(); 
	
	SpatialSearchResult(SpatialSearchResultsList parentList, int parentInd) {
		this.parentInd = parentInd;
		this.parent = parentList;
		for (int i = 0; i < parent.tCount; i++) {
			NameIndexAtom atom = parent.linearResults.get(parentInd * parentList.tCount + i);
			SpatialSearchToken token = parent.tokens[i];
			SpatialSearchResultRef ref = null;
			for (SpatialSearchResultRef ex : objs) {
				if (atom.id == ex.atom.id) {
					ref = ex;
					atom = null;
					break;
				}
			}
			if (ref == null) {
				ref = new SpatialSearchResultRef(atom);
				objs.add(ref);
			}
			ref.tokens.add(token);

		}
	}
	
	
	public static class SpatialSearchResultRef {
		public final NameIndexAtom atom;
		public final List<SpatialSearchToken> tokens = new ArrayList<>();
		
		public SpatialSearchResultRef(NameIndexAtom atom) {
			this.atom = atom;
		}
	}

	@Override
	public int compareTo(SpatialSearchResult o) {
		int res = Integer.compare(objs.size(), objs.size());
		if (res != 0) {
			return res;
		}
		return -Integer.compare(parentInd, o.parentInd);
	}
}
