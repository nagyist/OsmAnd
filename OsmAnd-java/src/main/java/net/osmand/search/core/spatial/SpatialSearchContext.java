package net.osmand.search.core.spatial;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.osmand.binary.BinaryMapAddressReaderAdapter.AddressRegion;
import net.osmand.binary.BinaryMapAddressReaderAdapter.CityBlocks;
import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.binary.BinaryMapPoiReaderAdapter.PoiRegion;
import net.osmand.binary.NameIndexReader;
import net.osmand.binary.NameIndexReader.PrefixNameValue;
import net.osmand.binary.OsmandOdb.AddressNameIndexDataAtom;
import net.osmand.binary.OsmandOdb.OsmAndPoiNameIndexDataAtom;
import net.osmand.data.Amenity;
import net.osmand.data.City;
import net.osmand.data.MapObject;
import net.osmand.search.core.spatial.SpatialSearchToken.NameIndexAtom;
import net.osmand.search.core.spatial.SpatialTextSearch.SpatialSearchFileCache;
import net.osmand.search.core.spatial.SpatialTextSearch.SpatialSearchGlobalCache;
import net.osmand.util.SearchAlgorithms;

// 1. TODO evict cache files
// 2. TODO evict cache words
// TODO properly calculate shiftToIndex (test) - Address
// TODO properly calculate shiftToIndex (test) - POI
public class SpatialSearchContext {

	public static boolean SEARCH_POI = true;
	
	public static boolean READ_POI_OBJECTS = true;
	public static boolean READ_ADDR_OBJECTS = true;
	
	// TODO implement for tokens
	public static boolean READ_COMMON_WORDS = false;
	
	public static int LIMIT_ATOMIC_OBJECTS = 2;
	
	
	private static int SHIFT_FILE_IND = 12; // maximum files 4096
	
	final List<BinaryMapIndexReader> files;
	
	final List<SpatialSearchFileCache> internalFile = new ArrayList<>();
	
	SpatialSearchStats stats = new SpatialSearchStats();
	
	public static class SpatialSearchStats {
		long time = System.nanoTime();
		long readTokensTime = 0;
		long readObjTime = 0;
		long computeTime = 0;
		long matchTime = 0;
		long atoms = 0;

		@Override
		public String toString() {
			return String.format("Search Stats %.1f ms - read tokens %.1f ms, read obj %.1f ms, match %.1f ms, comp %.1f ms", 
					time / 1e6, readTokensTime / 1e6, readObjTime / 1e6,
					matchTime / 1e6, computeTime / 1e6);
		}

		public void finish() {
			time = System.nanoTime() - time;
		}
	}
	
	public SpatialSearchContext(List<BinaryMapIndexReader> files) {
		this.files = files;
	}
	

	public void initFiles(SpatialSearchGlobalCache cache) {
		for (BinaryMapIndexReader bir : files) {
			SpatialSearchFileCache fc = cache.filesCache.get(bir.getFile().getName());
			if (fc == null || !fc.test(bir)) {
				fc = new SpatialSearchFileCache(bir);
			}
			cache.filesCache.put(fc.file, fc);
			this.internalFile.add(fc);
		}		
	}
	
	
	void readAtoms(List<SpatialSearchToken> tokens) throws IOException {
		for (SpatialSearchToken t : tokens) {
			for (int fileInd = 0; fileInd < files.size(); fileInd++) {
				SpatialSearchFileCache iCache = internalFile.get(fileInd);
				List<NameIndexReader> nameIndexes = iCache.tokens.get(t.word);
				if (nameIndexes == null) {
					stats.readTokensTime -= System.nanoTime();
					BinaryMapIndexReader b = files.get(fileInd);
					nameIndexes = new ArrayList<>();
					for (AddressRegion m : b.getAddressIndexes()) {
						nameIndexes.add(b.readFullNameIndex(m, t.word));
					}
					for (PoiRegion m : b.getPoiIndexes()) {
						nameIndexes.add(b.readFullNameIndex(m, t.word));
					}
					iCache.tokens.put(t.word, nameIndexes);
					stats.readTokensTime += System.nanoTime();
				}
				for (NameIndexReader indx : nameIndexes) {
					for (PrefixNameValue prefix : indx.getPrefixes()) {
						parseAtomSuffixes(t, fileInd, indx, prefix, tokens);
					}
				}
			}
		}
	}
	
	private long makeId(int fileInd, long shiftToIndex) {
		if (fileInd > 1 << SHIFT_FILE_IND) {
			throw new IllegalStateException();
		}
		long id = (shiftToIndex << SHIFT_FILE_IND) + SHIFT_FILE_IND;
		return id;
	}
	

	private void parseAtomSuffixes(SpatialSearchToken t, int fileInd, 
			NameIndexReader indx, PrefixNameValue prefix, List<SpatialSearchToken> allTokens) throws IOException {
		String curSuffix = null;
		List<String> suffixes = new ArrayList<>();
		List<String> commonSuffixes = new ArrayList<>();
		boolean addr = prefix.addr != null;
		for (String s : addr ? prefix.addr.getSuffixesDictionaryList() : 
				prefix.poi.getSuffixesDictionaryList()) {
			curSuffix = SearchAlgorithms.nameIndexDecodeDictionarySuffix(curSuffix, s);
			suffixes.add(prefix.key + curSuffix);
		}
		for (Integer i : addr ? prefix.addr.getSuffixesCommonDictionaryList()
				: prefix.poi.getSuffixesCommonDictionaryList()) {
			commonSuffixes.add(indx.getCommonIndexed(i));
		}
		if (addr) {
			for (AddressNameIndexDataAtom a : prefix.addr.getAtomList()) {
				long shift = prefix.shift - a.getShiftToIndex(0);
				long lid = makeId(fileInd, prefix.shift - a.getShiftToIndex(0));
				MapObject obj = null;
				if (READ_ADDR_OBJECTS) {
					obj = readAddrObject(fileInd, indx, prefix, a, shift, obj);
				}
				parseSuffixes(t, suffixes, commonSuffixes, a, null, lid, obj, allTokens);
			}
		} else if (SEARCH_POI) {
			for (OsmAndPoiNameIndexDataAtom a : prefix.poi.getAtomsList()) {
				long shift = BinaryMapIndexReader.convertFixed32ToRef(a.getShiftTo()); 
				long lid = makeId(fileInd, shift + a.getPoiIndInBlock(0));
				MapObject amenity = null;
				if (READ_POI_OBJECTS) {
					List<Amenity> lst = files.get(fileInd).readAmenityBlock(indx.poiRegion, shift);
					amenity = lst.get(a.getPoiIndInBlock(0));
				}
				parseSuffixes(t, suffixes, commonSuffixes, null, a, lid, amenity, allTokens);
			}
		}
	}

	private MapObject readAddrObject(int fileInd, NameIndexReader indx, PrefixNameValue prefix,
			AddressNameIndexDataAtom a, long shift, MapObject obj) throws IOException {
		// TODO read by id
		long tm = System.nanoTime();
		if (a.getType() == CityBlocks.STREET_TYPE.index) {
			long cshift = prefix.shift - a.getShiftToCityIndex(0);
			City city = files.get(fileInd).readCityObject(indx.addressRegion, cshift);
			obj = files.get(fileInd).readStreetObject(indx.addressRegion, city, shift);
		} else if (a.getType() == CityBlocks.BOUNDARY_TYPE.index ||
				a.getType() == CityBlocks.CITY_TOWN_TYPE.index || 
				a.getType() == CityBlocks.VILLAGES_TYPE.index || 
				a.getType() == CityBlocks.POSTCODES_TYPE.index) {
			obj = files.get(fileInd).readCityObject(indx.addressRegion, shift);
		}
		stats.readObjTime += (System.nanoTime() - tm);
		return obj;
	}

	private void parseSuffixes(SpatialSearchToken t, List<String> suffixes, List<String> commonSuffixes,
			AddressNameIndexDataAtom a, OsmAndPoiNameIndexDataAtom b, long lid, MapObject obj, List<SpatialSearchToken> allTokens) {
		int cnt = a != null ? a.getSuffixesBitsetIndexCount() : b.getSuffixesBitsetIndexCount();
		String name = "";
		for (int i = 0; i < cnt; i++) {
			int suffBit = a != null ? a.getSuffixesBitsetIndex(i) : b.getSuffixesBitsetIndex(i);
			if (suffBit % 2 == 0) {
				int ind = suffBit / 2 - 1;
				if (ind == -1) {
					if (acceptName(t, name)) {
						addObject(t, a, b, lid, obj, name, allTokens);
					}
					name = "";
				} else if (ind < suffixes.size()) {
					name += suffixes.get(ind);
				} else {
					// common suffix
					name += " " + commonSuffixes.get(ind - suffixes.size());
				}
			} else {
				if (suffBit % 4 == 1) {
					// separated number
					name += " " + (suffBit >> 2);
				} else {
					// partial
					name += (suffBit >> 2);
				}
			}
		}
		if (name.length() != 0 && acceptName(t, name)) {
			addObject(t, a, b, lid, obj, name, allTokens);
		}
	}

	private boolean acceptName(SpatialSearchToken t, String name) {
		stats.matchTime -= System.nanoTime();
		boolean acceptName = t.acceptName(name);
		stats.matchTime += System.nanoTime();
		return acceptName;
	}


	private void addObject(SpatialSearchToken t, AddressNameIndexDataAtom a, OsmAndPoiNameIndexDataAtom b, long lid,
			MapObject obj, String name, List<SpatialSearchToken> allTokens) {
		NameIndexAtom atom = new NameIndexAtom(name, a, b, lid, obj);
		t.addAtom(atom);
		if (name.indexOf(' ') != -1) {
			List<String> split = SearchAlgorithms.splitAndNormalize(name);
			for (int k = 1; k < split.size(); k++) {
				// not exactly correct as does combination but not a strong issue 
				for (SpatialSearchToken token : allTokens) {
					if (t != token && acceptName(token, name)) {
						token.addAtom(atom);
					}
				}
			}
		}

	}



}