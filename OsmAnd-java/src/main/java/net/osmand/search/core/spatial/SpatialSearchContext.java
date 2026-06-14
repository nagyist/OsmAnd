package net.osmand.search.core.spatial;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.binary.NameIndexInspector;
import net.osmand.binary.BinaryMapAddressReaderAdapter.AddressRegion;
import net.osmand.binary.BinaryMapPoiReaderAdapter.PoiRegion;
import net.osmand.binary.NameIndexInspector.PrefixNameValue;
import net.osmand.binary.OsmandOdb.AddressNameIndexDataAtom;
import net.osmand.binary.OsmandOdb.OsmAndPoiNameIndexDataAtom;
import net.osmand.search.core.spatial.SpatialTextSearch.NameIndexAtom;
import net.osmand.search.core.spatial.SpatialTextSearch.SpatialSearchToken;
import net.osmand.util.SearchAlgorithms;

// 1. TODO evict cache files
// 2. TODO evict cache words
// TODO properly calculate shiftToIndex (test) - Address
// TODO properly calculate shiftToIndex (test) - POI
public class SpatialSearchContext {

	public static boolean SEARCH_POI = true;
	private static int SHIFT_FILE_IND = 12; // maximum files 4096
	
	final List<BinaryMapIndexReader> files;
	
	final List<SearchManyFileCache> internalFile = new ArrayList<>();
	
	SearchManyStats stats = new SearchManyStats();
	
	SearchManyGlobalCache cache; // reusable between sessions
	
	public static class SearchManyFileCache {
		public final String file;
		public final long length;
		public final long edition;
		public Map<String, List<NameIndexInspector>> tokens = new HashMap<>(); 
		
		public SearchManyFileCache(BinaryMapIndexReader r) {
			file = r.getFile().getName();
			length = r.getFile().length();
			edition = r.getDateCreated();
		}
		
		public boolean test(BinaryMapIndexReader r) {
			return r.getFile().getName().equals(file) && r.getFile().length() == length && 
					r.getDateCreated() == edition;
		}
	}
	
	
	public static class SearchManyGlobalCache {
		
		public Map<String, SearchManyFileCache> filesCache = new HashMap<>();
		
	}
	
	public static class SearchManyStats {
		long time = System.nanoTime();
		long readTime = 0;
		long computeTime = 0;

		@Override
		public String toString() {
			return String.format("Search Stats %.1f ms - read %.1f ms, comp %.1f ms", time / 1e6, readTime / 1e6,
					computeTime / 1e6);
		}

		public void finish() {
			time = System.nanoTime() - time;
		}
	}
	
	public SpatialSearchContext(List<BinaryMapIndexReader> files, SearchManyGlobalCache cache) {
		this.cache = cache;
		this.files = files;
		
		for (BinaryMapIndexReader bir : files) {
			SearchManyFileCache fc = cache.filesCache.get(bir.getFile().getName());
			if (fc == null || !fc.test(bir)) {
				fc = new SearchManyFileCache(bir);
			}
			cache.filesCache.put(fc.file, fc);
			this.internalFile.add(fc);
		}
	}
	
	public SpatialSearchContext(List<BinaryMapIndexReader> files) {
		this(files, new SearchManyGlobalCache());
	}
	
	void readAtoms(SpatialSearchToken t) throws IOException {
		for (int fileInd = 0; fileInd < files.size(); fileInd++) {
			SearchManyFileCache iCache = internalFile.get(fileInd);
			List<NameIndexInspector> nameIndexes = iCache.tokens.get(t.word);
			if (nameIndexes == null) {
				stats.readTime -= System.nanoTime();
				BinaryMapIndexReader b = files.get(fileInd);
				nameIndexes = new ArrayList<>();
				for (AddressRegion m : b.getAddressIndexes()) {
					nameIndexes.add(b.readFullNameIndex(m, t.word));
				}
				for (PoiRegion m : b.getPoiIndexes()) {
					nameIndexes.add(b.readFullNameIndex(m, t.word));
				}
				iCache.tokens.put(t.word, nameIndexes);
				stats.readTime += System.nanoTime();
			}
			for (NameIndexInspector indx : nameIndexes) {
				for (PrefixNameValue prefix : indx.getPrefixes()) {
					parseAtomSuffixes(t, fileInd, prefix);
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
	

	private void parseAtomSuffixes(SpatialSearchToken t, int fileInd, PrefixNameValue prefix) {
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
							String name = suffixes.get(ind);
							long lid = makeId(fileInd, prefix.shift - a.getShiftToIndex(0));
							t.addAtom(name, new NameIndexAtom(name, a, null, lid));
						}
						suffBit >>>= 1;
					}
				}
			}
		} else if (SEARCH_POI) {
			for (OsmAndPoiNameIndexDataAtom a : prefix.poi.getAtomsList()) {
				for (int i = 0; i < a.getSuffixesBitsetCount(); i++) {
					int suffBit = a.getSuffixesBitset(i);
					for (int j = 0; j < INT_BITS && suffBit != 0; j++) {
						if (suffBit % 2 == 1) {
							int ind = i * INT_BITS + j;
							String name = suffixes.get(ind);
							long lid = makeId(fileInd, a.getShiftTo());
							t.addAtom(name, new NameIndexAtom(name, null, a, lid));
						}
						suffBit >>>= 1;
					}
				}
			}
		}
	}

}