package net.osmand.search.core.spatial;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import net.osmand.binary.BinaryMapPoiReaderAdapter;
import net.osmand.data.Building;
import net.osmand.data.MapObject;
import net.osmand.data.Street;
import net.osmand.search.core.HashQuadTree;
import net.osmand.search.core.spatial.SpatialSearchToken.NameIndexAtom;
import net.osmand.search.core.spatial.SpatialTextSearch.SpatialTextSearchSettings;
import static net.osmand.search.core.spatial.SpatialSearchToken.BUILDING_TYPE;
import static net.osmand.search.core.spatial.SpatialSearchToken.STREET_TYPE;

public class SpatialSearchResultsList implements Comparable<SpatialSearchResultsList> {
	final SpatialSearchToken[] tokens; // non modifieable!
	final int tCount;
	

	// NameIndexAtom[][] -- should be double array to store list of combinations
	List<NameIndexAtom> linearResults = new ArrayList<>();
	TLongArrayList tileIds = new TLongArrayList();
	TIntArrayList tileZooms = new TIntArrayList();
	HashQuadTree<Integer> quadTree = new HashQuadTree<>(16);

	TIntObjectHashMap<Boolean> skipResults = new TIntObjectHashMap<>();
	List<SpatialSearchResult> finalResult = null;
	
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
		if (parent != null) {
			calculateIntersection(token, parent);
		}
	}
	
	private void loadObjects(SpatialSearchContext ctx, int type, TLongObjectHashMap<MapObject> cache) throws IOException {
		TLongObjectHashMap<Long> lstMap = new TLongObjectHashMap<>();
		
		Map<Integer, TLongHashSet> poiBboxes = new LinkedHashMap<Integer, TLongHashSet>();
		for (NameIndexAtom a : linearResults) {
			if (a.object != null) {
				cache.put(a.id, a.object);
				continue;
			}
			if (a.type == SpatialSearchToken.POI_TYPE) {
				int indInd = ctx.getFileInd(a.id);
				if(!poiBboxes.containsKey(indInd)) {
					poiBboxes.put(indInd, new TLongHashSet());
				}
				poiBboxes.get(indInd).add(HashQuadTree.encodeTileId31(BinaryMapPoiReaderAdapter.EVAL_TAG_GROUP_ZOOM,
						a.coords.x16 << 15, a.coords.y16 << 15));
			}
			if (a.type == type || (type == -2 && a.type != SpatialSearchToken.POI_TYPE
					&& a.type != SpatialSearchToken.STREET_TYPE)) {
				lstMap.put(a.id, a.parentid);
			} else if(type == -2 && a.type == SpatialSearchToken.STREET_TYPE) {
				lstMap.put(a.parentid, (long) 0);
			}
		}
		for (Map.Entry<Integer, TLongHashSet> poiBoxEntry : poiBboxes.entrySet()) {
			ctx.readPOIBboxes(poiBoxEntry.getKey(), poiBoxEntry.getValue());
		}
		TLongArrayList lst = new TLongArrayList(lstMap.keySet());
		lst.sort(); // sort is not correct for file ind last bits >>> 12 
		for(int i = 0; i < lst.size(); i++) {
			long id = lst.get(i);
			if (type == SpatialSearchToken.POI_TYPE) {
				cache.put(id, ctx.readPoiObject(id, cache));
			} else {
				cache.put(id, ctx.readAddrObject(id, lstMap.get(id), cache));
			}
		}
		for (NameIndexAtom a : linearResults) {
			MapObject mo = cache.get(a.id);
			if (mo != null) {
				a.object = mo;
			}
		}
	}
	
	public void loadObjects(SpatialSearchContext ctx) throws IOException {
		TLongObjectHashMap<MapObject> cache = new TLongObjectHashMap<MapObject>();
		loadObjects(ctx, SpatialSearchToken.POI_TYPE, cache);
		cache.clear();
		loadObjects(ctx, -2, cache);
		loadObjects(ctx, SpatialSearchToken.STREET_TYPE, cache);
	}
	
	public List<SpatialSearchToken> getMissingTokens(SpatialSearchContext ctx) {
		List<SpatialSearchToken> lst = new ArrayList<>(ctx.tokens);
		for (SpatialSearchToken s : tokens) {
			lst.remove(s);
		}
		return lst;
	}
	
	public void loadObjectsAndCalcBuildings(SpatialSearchContext ctx) throws IOException {
		ctx.stats.loadObjectsBld -= System.nanoTime();
		loadObjects(ctx);
		
		if (SpatialTextSearchSettings.SEARCH_BUILDINGS) {
			Map<String, Building> bldCheckCache = new HashMap<>();
			for (int indx = 0; indx < getCombinations(); indx++) {
				calcBuilding(indx, bldCheckCache);
			}
		}
		ctx.stats.loadObjectsBld += System.nanoTime();
	}
	
	private void calcBuilding(int indx, Map<String, Building> bldCheckCache) {
		Map<NameIndexAtom, String> bldCheckMap = null;
		for (int i = 0; i < tCount; i++) {
			NameIndexAtom bld = linearResults.get(indx * tCount + i);
			if (bld.buildingInd >= 0) {
				int strTokenInd = getTokenByOriginalOrder(bld.buildingInd);
				if (strTokenInd < 0) {
					skipResults.put(indx, true);
					break;
				}
				NameIndexAtom str = linearResults.get(indx * tCount + strTokenInd);
				if (str.id != bld.id) {
					continue;
				}
				if (bldCheckMap == null) {
					bldCheckMap = new HashMap<>();
				}
				String searchKey = bldCheckMap.get(str);
				if (searchKey == null) {
					searchKey = tokens[i].word;
				} else {
					searchKey += " " + tokens[i].word;
				}
				bldCheckMap.put(str, searchKey);
			}
		}
		// check many buildings on same street possibly unit or ref
		if (bldCheckMap != null) {
			Iterator<Entry<NameIndexAtom, String>> it = bldCheckMap.entrySet().iterator();
			while(it.hasNext()) {
				Entry<NameIndexAtom, String> e = it.next();
				NameIndexAtom str = e.getKey();
				String bldName = e.getValue();
				String cacheKey = str.id + " " + bldName;
				Building bldObj = null;
				if (bldCheckCache.containsKey(cacheKey)) {
					bldObj = bldCheckCache.get(cacheKey);
				} else {
					bldObj = checkBuilding((Street) str.object, bldName);
					if (bldObj == null) {
//						System.out.printf("No building '%s': %s\n", bldName, str.object);
					} else {
//						System.out.printf("Building found '%s' -'%s': %s\n", bldObj, bldName, str.object);
					}
					bldCheckCache.put(cacheKey, bldObj);
				}
				if (bldObj == null) {
					skipResults.put(indx, true);
					break;
				} else {
					for (int i = 0; i < tCount; i++) {
						NameIndexAtom bld = linearResults.get(indx * tCount + i);
						if (bld.buildingInd >= 0 && str.id == bld.id) {
							bld.object = bldObj;
							bld.name = bldObj.getName();
							if (bldObj.isInterpolation()) {
								bld.name = bldName;
							}
						}
					}
				}
			}
		}
	}
	
	// Split '18B', '18/B', '18-B', '18 B' -> ['18', 'B']
	public static Set<String> getBuildingCompareSet(String name) {
		Set<String> resultSet = null;
		StringBuilder currentToken = new StringBuilder();
		int lastType = 0;
		for (int i = 0; i < name.length(); i++) {
			char ch = name.charAt(i);
			int type = Character.isDigit(ch) ? 1 : (Character.isLetter(ch) ? 2 : 0);
			boolean addToken = false;
			if (type != lastType) {
				addToken = true;
			}
			if (addToken && currentToken.length() > 0) {
				if (resultSet == null) {
					resultSet = new TreeSet<String>();
				}
				resultSet.add(currentToken.toString().toLowerCase());
				currentToken.setLength(0); // Clear buffer
			}
			if (type > 0) {
				currentToken.append(ch);
			}
			lastType = type;
		}
		if (currentToken.length() > 0) {
			if (resultSet == null) {
				return Collections.singleton(currentToken.toString().toLowerCase());
			}
			resultSet.add(currentToken.toString().toLowerCase());
		}
		if (resultSet == null) {
			return Collections.singleton(name.toLowerCase());
		}
		return resultSet;
	}
	
	private Building checkBuilding(Street street, String bld) {
		Building interpolation = null;
		Building partial = null;
		Set<String> original = getBuildingCompareSet(bld);
		for (Building b : street.getBuildings()) {
			if (b.isInterpolation()) {
				// interpolation only over 1 set
				if (original.size() == 1 && b.belongsToInterpolation(original.iterator().next())) {
					interpolation = b;
				}
			} else {
				Set<String> cmp = getBuildingCompareSet(b.getName());
				if (cmp.equals(original)) {
					// exact
					return b;
				}
				if (cmp.size() == original.size() + 1) {
					// case data only 18 present, 18-B searched
					if (cmp.containsAll(original)) {
						partial = b;
					}
				} else if (cmp.size() + 1 == original.size()) {
					// case data only 18-B present, 18 searched 
					if (original.containsAll(cmp)) {
						partial = b;
					}
				}
			}
		}
		if (partial != null) {
			return partial;
		}
		if (interpolation != null) {
			return interpolation;
		}
		return null;
	}

	private int getTokenByOriginalOrder(int originalOrder) {
		for(int ind = 0; ind < tokens.length; ind++) {
			if (tokens[ind].originalOrder == originalOrder) {
				return ind;
			}
		}
		return -1;
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
	
	public List<SpatialSearchResult> getFinalResult() {
		return finalResult;
	}

	public List<SpatialSearchResult> sortResults(SpatialSearchContext ctx, boolean deduplicate) {
		finalResult = new ArrayList<>(tileIds.size());
		for (int i = 0; i < tileIds.size(); i++) {
			if (!skipResults.containsKey(i)) {
				finalResult.add(new SpatialSearchResult(this, i));
			}
		}		
		finalResult = sortResults(ctx, finalResult, deduplicate);
		return finalResult;
	}

	public List<SpatialSearchResult> sortResults(SpatialSearchContext ctx, List<SpatialSearchResult> finalResult, boolean deduplicate) {
		Collections.sort(finalResult, (o1, o2) -> SpatialSearchResult.compare(o1, o2, ctx.location));
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
	
	private void calculateIntersection(SpatialSearchToken token, SpatialSearchResultsList parent) {
		if (parent.getTokenCount() == 0) {
			addResult(null, 0, token, token.atoms);
		} else if (parent.getCombinations() > 0) {
			// 1. iterate parent objects and find all objects from <parent>
			//    that are fully inside object <token> or have same the same tile 
			for (int i = 0; i < parent.tileIds.size(); i++) {
				long tileId = parent.tileIds.get(i);
				int zoom = parent.tileZooms.get(i);
				final int indx = i;
				token.quadTree.forEachMatch(zoom, tileId, t -> {
					addResult(parent, indx, token, t);
				});
			}
			// 2. reverse search quad tree from <token> and find objects
			//    that are fully inside any object <parent> and not the same the same tile!
			final SpatialSearchResultsList p = parent;
			for (final NameIndexAtom a : token.atoms) {
				parent.quadTree.forEachMatchHigherZoom(a.coords.bboxTileZoom, a.coords.bboxTileId, indxs -> {
					for (int indx : indxs) {
						addResult(p, indx, token, a);
					}
				});
			}
		}		
	}


	void addResult(SpatialSearchResultsList parent, int indx, SpatialSearchToken token, List<NameIndexAtom> atoms) {
		for (NameIndexAtom a : atoms) {
			addResult(parent, indx, token, a);
		}
	}

	public int sumTokenAtomSize() {
		int sum = 0;
		for (SpatialSearchToken s : tokens) {
			sum += s.atoms.size();
		}
		return sum;
	}

	boolean addResult(SpatialSearchResultsList parent, int pindx, SpatialSearchToken token, NameIndexAtom a) {
		boolean acceptIntersection = acceptIntersection(parent, pindx, token, a);
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

	private boolean acceptIntersection(SpatialSearchResultsList parent, int pindx, SpatialSearchToken token, NameIndexAtom a) {
		// 1. Precise intersection
		// no cache for parent now needed
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
		// TODO 2/1 21038 Sokak
		// 2. Don't allow intersect potential building with other object
		for (int i = 0; parent != null && i < parent.tCount; i++) {
			NameIndexAtom pa = parent.linearResults.get(pindx * parent.tCount + i);
			if (pa.id == a.id) {
				continue;
			}
			// pa and a using same tokens for street & house but different streets
			if (parent.tokens[i].originalOrder == a.buildingInd) {
				return false;
			} else if (pa.buildingInd == token.originalOrder) {
				return false;
			}
			// don't intersect building with other street
			if (pa.buildingInd >= 0 && (a.type == BUILDING_TYPE || a.type == STREET_TYPE)) {
				return false;
			} else if (a.buildingInd >= 0 && (pa.type == BUILDING_TYPE || pa.type == STREET_TYPE)) {
				return false;
			} 
		}
		
		// 3. Duplicate words		
		for (int i = 0; parent != null && i < parent.tCount; i++) {
			NameIndexAtom pa = parent.linearResults.get(pindx * parent.tCount + i);
			if (pa.id == a.id && tokens[0].word.equals(tokens[i + 1].word)) {
				// NameIndexAtom supports "<word> <...> <word>" but it's not present in DATA now
				int indexOf = a.name.indexOf(tokens[0].word, pindx);
				if (indexOf != -1 && a.name.indexOf(tokens[0].word, indexOf + 1) >= 0) {
					// duplicate name in object
				} else {
					return false;
				}
			}
			if (!pa.coords.intersects(a.coords)) {
				intersect = false;
				break;
			}
		}
		
		// 4. ignore multiple atomic objects intersections POI / Streets > 2!
		//    Building + Street (counts as 2 objects) - no (Building + Street 1 + Street 2)
		if (a.atomicObject()) {
			// check limit atomic objects to add
			List<Long> objects = new ArrayList<Long>(SpatialTextSearchSettings.LIMIT_ATOMIC_OBJECTS);
			objects.add(a.id);
			for (int i = 0; parent != null && i < parent.tCount; i++) {
				NameIndexAtom pa = parent.linearResults.get(pindx * parent.tCount + i);
				if (pa.atomicObject()) {
					long id = pa.id;
					if (pa.buildingInd >= 0) {
//						id += Integer.MAX_VALUE;
					}
					if (!objects.contains(id)) {
						objects.add(id);
					}
					//    Don't intersect <City Street> ('<Salt Lake City>') with Street ('Pennsylvania street')
					if ((a.isCityStreetName() && pa.id != a.id) || (pa.isCityStreetName() && a.id != pa.id)) {
						return false;
					}
				}
				if (objects.size() > SpatialTextSearchSettings.LIMIT_ATOMIC_OBJECTS) {
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
		return String.format("Results %d tokens %,d%s - %s %s", tCount, getCombinations(),
				finalResult == null ? "" : String.format(" (%,d unique)", finalResult.size()), 
				words, !extended ? "" : (" results: " + linearResults));
	}
	
	

	@Override
	public String toString() {
		return toString(false);
	}

	

	
}