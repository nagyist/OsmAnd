package net.osmand.search.core.spatial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.osmand.binary.ObfConstants;
import net.osmand.data.MapObject;
import net.osmand.data.Street;
import net.osmand.search.core.spatial.SpatialSearchToken.NameIndexAtom;

public class SpatialSearchResult implements Comparable<SpatialSearchResult> {

	public static boolean USE_ORDER_TYPE = true;
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
		if (USE_ORDER_TYPE) {
			useSmallestObjectOrder();
		} else {
			useOriginalOrder();
		}
	}
	
	void useSmallestObjectOrder() {
		for (SpatialSearchResultRef r : objs) {
			Collections.sort(r.tokens, (o1, o2) -> Integer.compare(o1.originalOrder, o2.originalOrder));
		}
		Collections.sort(objs,
				(o1, o2) -> Integer.compare(o1.typeOrder(), o2.typeOrder()));
	}

	void useOriginalOrder() {
		for (SpatialSearchResultRef r : objs) {
			Collections.sort(r.tokens, (o1, o2) -> Integer.compare(o1.originalOrder, o2.originalOrder));
		}
		Collections.sort(objs,
				(o1, o2) -> Integer.compare(o1.tokens.get(0).originalOrder, o2.tokens.get(0).originalOrder));

	}
	
	public List<MapObject> getObjects() {
		List<MapObject> o = new ArrayList<>();
		for (SpatialSearchResultRef r : objs) {
			o.add(r.atom.object);
		}
		return o;
	}

	@Override
	public String toString() {
		return objs.toString();
	}
	
	
	public static class SpatialSearchResultRef {
		public final NameIndexAtom atom;
		public final List<SpatialSearchToken> tokens = new ArrayList<>();
		
		public SpatialSearchResultRef(NameIndexAtom atom) {
			this.atom = atom;
		}
		
		public int typeOrder() {
			if (atom.type == SpatialSearchToken.POI_TYPE) {
				return 0;
			}
			if (atom.type == SpatialSearchToken.POI_TYPE) {
				return 1;
			}
			return 2;
		}
		


		@Override
		public String toString() {
			List<String> words = new ArrayList<String>();
			for (SpatialSearchToken s : tokens) {
				words.add(s.word);
			}
			if (atom.object != null) {
				return String.format("%s %s (%d) %.4f %.4f ", words, 
						atom.object.getClass().getSimpleName() + " " + atom.object.getName(), 
						atom.object instanceof Street ? atom.object.getId() : ObfConstants.getOsmObjectId(atom.object),
						atom.object.getLocation().getLatitude(),
						atom.object.getLocation().getLongitude());
			}
			return atom.simpleName(words.toString()); 
		}
	}
	

	public int sumOther() {
		int s1 = 0;
		for (SpatialSearchResultRef r : objs) {
			s1 += r.atom.otherWordsCnt;
		}
		return s1;
	}
	
	public int sumTypeOrder() {
		int s1 = 0;
		for (SpatialSearchResultRef r : objs) {
			s1 += r.typeOrder();
		}
		return s1;
	}

	@Override
	public int compareTo(SpatialSearchResult o) {
		int res = Integer.compare(objs.size(), o.objs.size());
		if (res != 0) {
			return res;
		}
		res = -Integer.compare(sumTypeOrder(), o.sumTypeOrder());
		if (res != 0) {
			return res;
		}
		
		res = Integer.compare(sumOther(), o.sumOther());
		if (res != 0) {
			return res;
		}
		return -Integer.compare(parentInd, o.parentInd);
	}
}
