package net.osmand.search.core.spatial;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.ByteString;

import gnu.trove.map.hash.TLongObjectHashMap;
import net.osmand.CollatorStringMatcher;
import net.osmand.CollatorStringMatcher.StringMatcherMode;
import net.osmand.binary.BinaryMapAddressReaderAdapter.CityBlocks;
import net.osmand.binary.OsmandOdb.AddressNameIndexDataAtom;
import net.osmand.binary.OsmandOdb.OsmAndPoiNameIndexDataAtom;
import net.osmand.data.MapObject;
import net.osmand.search.core.HashQuadTree;
import net.osmand.util.MapUtils;
import net.osmand.util.SearchAlgorithms;

public class SpatialSearchToken {
	int originalOrder = 0;
	int sortedOrder = 0;
	boolean incomplete;
	String originalWord;
	String word;
	List<NameIndexAtom> atoms = new ArrayList<>();
	TLongObjectHashMap<NameIndexAtom> index = new TLongObjectHashMap<>();
	HashQuadTree<NameIndexAtom> quadTree = new HashQuadTree<>(16);
	CollatorStringMatcher collator;

	public SpatialSearchToken(String w, String original, int order) {
		originalWord = original;
		word = w;
		this.originalOrder = order;
		if (w.endsWith(".")) {
			collator = new CollatorStringMatcher(w, StringMatcherMode.CHECK_STARTS_FROM_SPACE);
		} else {
			collator = new CollatorStringMatcher(w, StringMatcherMode.CHECK_EQUALS_FROM_SPACE);
		}
	}

	@Override
	public String toString() {
		return String.format("%d. %s - %d atoms", originalOrder, originalWord, atoms.size());
	}

	void addAtom(NameIndexAtom atom) {
		NameIndexAtom aa = index.get(atom.id);
		if (aa != null) {
			if (aa != atom) {
//					throw new IllegalStateException(aa.name + " != " + atom.name  + " " + aa.object + " " + aa.object.getLocation());
			} else {
				return;
			}
		}
		index.put(atom.id, atom);
		atoms.add(atom);
		quadTree.put(atom.bboxTileZoom, atom.bboxTileId, atom);
	}

	boolean acceptName(String name) {
		return collator.matches(name);
	}
	
	
	public static class NameIndexAtom {
		String name;
		AddressNameIndexDataAtom addr;
		OsmAndPoiNameIndexDataAtom poi;
		
		long id;
		MapObject object;
		// TODO clculate
		int otherWordsCnt = 0;
		
		int[] bbox31; // if exists [xleft, yleft, xright, yright]
		long bboxTileId; // encodes zoom, tileX, tileY
		int bboxTileZoom;
		int x16, y16;

		NameIndexAtom(String name, AddressNameIndexDataAtom addr, OsmAndPoiNameIndexDataAtom poi, 
				long id, MapObject obj) {
			this.name = name;
			this.addr = addr;
			this.poi = poi;
			this.id = id;
			this.object = obj;
			calculateBbox(addr, poi);
		}
		
		public boolean atomicObject() {
			return (addr != null && addr.getType() == CityBlocks.STREET_TYPE.index) || poi == null;  
					
		}
		
		private void calculateBbox(AddressNameIndexDataAtom addr, OsmAndPoiNameIndexDataAtom poi) {
			if (addr != null && addr.getXy16Count() >= 1) {
				int xy16 = addr.getXy16(0);
				this.x16 = (xy16 >>> 16);
				this.y16 = (xy16 & ((1 << 16) - 1));
				ByteString bbox = addr.getBbox();
				bboxTileZoom = 15;
				bboxTileId = HashQuadTree.encodeTileId(bboxTileZoom, x16 / 2, y16 / 2);
				if (bbox != null && addr.hasBbox()) {
					bbox31 = SearchAlgorithms.decodeBboxForNameAtomsBytes(bbox, x16, y16);
					if (bbox31 != null) {
						int z = 31;
						int xleft = bbox31[0], xright = bbox31[2];
						int ytop = bbox31[1], ybottom = bbox31[3];
						while (xleft != xright || ytop != ybottom) {
							z--;
							xleft >>= 1;
							xright >>= 1;
							ytop >>= 1;
							ybottom >>= 1;
						}
						bboxTileZoom = z;								
						bboxTileId = HashQuadTree.encodeTileId(z, xleft, ytop);
					}
				}
			} else if (poi != null) {
				this.x16 = poi.getX();
				this.y16 = poi.getY();
				bboxTileZoom = 16;
				bboxTileId = HashQuadTree.encodeTileId(bboxTileZoom, x16, y16);
			}			
		}
		
		String simpleName(String name) {
			String type = "";
			if (addr != null) {
				type = CityBlocks.getByType(addr.getType()).toString();
			} else if (poi != null) {
				type = "POI";
			}
			return String.format("%s %s %d (%.4f, %.4f)", type, name, (id % 0xffff),
					MapUtils.get31LatitudeY(y16 << 15), MapUtils.get31LongitudeX(x16 << 15));
		}
		
		@Override
		public final String toString() {
			return object != null ? object.toString() : simpleName(name); 
		}


		
	};
	
	

}