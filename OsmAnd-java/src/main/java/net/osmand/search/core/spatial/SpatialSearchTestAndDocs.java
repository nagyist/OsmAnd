package net.osmand.search.core.spatial;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.data.City;
import net.osmand.data.MapObject;
import net.osmand.map.OsmandRegions;
import net.osmand.search.core.spatial.SpatialTextSearch.SpatialSearchResults;
import net.osmand.search.core.spatial.SpatialTextSearch.SpatialTextSearchSettings;
import net.osmand.util.SearchAlgorithms;


//////////// OTHER TASKS ///////
// - TEST / REVIEW duplicate words in query Pennsylvania Street in Pennsylvania +
// - TEST / REVIEW - TOKENIZER (split) - COLLATOR: '#3', 'str.', 'U.S. Bank' ,'2-st' vs '2'  (Unit tests)
// - REVIEW SPLIT: if POI / Address is searched correctly - split Words - splitAndNormalizeSearchQuery(SearchPhrase.ALLDELIMITERS_WITH_HYPHEN);
//    - 2-га Нова (2 Нова), Бульварно-Кудрявськаб NC-42 
// - TEST / REVIEW - Numbers - isNumber2Letters '#3', and other
// - TEST / REVIEW - Unit test (<common_word> <almost_number>) -('№25'??, '25', '#25'?) -- +('школа', 'школа №25',  'школа 25')

//////////// TESTING //////////

// IN PROGRESS 
// Building entrances refs ', 3' ("18/32, 2")
// Building units ('-') search 'Holmby 18 A', 'Holmby 18-A', 'Holmby 18A'
// TODO salt lake 2 Street <Salt Lake City>(-111651)


// SLOW
// TODO DISABLED Read common = true ->  Calle 20 188 Lima San Isidro (VERY SLOW) - 'New york The plaza'
// TODO Sokak very slow (Sokak 23018. intersection)
// TODO! Introduce limit if intersections grow too fast > 5K (Calle x Calle) limit by distance (TEST)
// FIXME 'New york s.' - 1 letter very slow

// TODO Progress / cancel

// TO DO
// FIXME Abbreviations Phase
// FIXME POI Categories + top poi categories
// FIXME Building interpolation - name / location
// FIXME Street intersection match
// FIXME Combine by osmid (poi type internet) & wikidata id ? osm id for routes (?)
//       Combine regions.ocbf (boundary)
// TODO Ignore same embedded boundary city / county - deduplicate on the fly
// TODO test: merge boundaries bbox - extend incomplete boundary same id...
// TODO Test/Review Match '2nd' and '2'
// TODO Issues Nova poshta Kharkiv

// EXTRA FEATURES
// TODO Search in large parks, neighborhood same as in boundaries (index bbox POI), residential way/56238205
// TODO Postcode needs to load street and check buildings! Store postcode as bbox not as City! - '1186RZ 324' (NL, UK) 
// TODO Search near key objects (subway station artificial bbox)
// TODO Add flats: https://www.openstreetmap.org/node/5843642738
// TODO Sugggestion-correction
// TODO POI Categories translations / synonyms
// TODO Web add regions.ocbf and 2nd search to search (Ksenia) - test "Arizona"
// TODO English postcodes

// TEST IDEAS
// TODO Not forget to include regions.ocbf on client
// TODO finish index_words_dashboard.html
// TODO Include high rating objects in world basemap (Eiffel Tour)
// TODO Geocoding ("NC 42" == "NC-42") - Use new search ?
// TODO ? review settings: read objects after some intersections (but not too early)
//      - Results 5 tokens 1,949 (139 unique) - compact objects during combinations?
// TODO ? in the end recheck bbox boundary (full?) after load coordinates 31 (not 15) - chernihiv sport life
// TODO ? Store wikidata id for boundaries (regions.ocbf) & display them ? 

public class SpatialSearchTestAndDocs {

	/**
	 * Tokenizer {@link SearchAlgorithms#splitAndNormalize(String, boolean)}
	 * 
	 * Word: Characters or digits (emoji undefined status) 
	 * Special symbols:
	 * 
	 * '.' - part of the word: 'st.', '2039.' (needs to be stored)
	 * ''' - part of the word: 'Mcdonald's' ()
	 * '-' - split not numbers, for numbers part of the word 
	 * '/' - split for not numbers, for numbers part of the word 
	 * Example: split used for user input '63/28' should keep as 1 word for building
	 * Specialeeds to be stored but ignored in collator
	 * 
	 * Other symbols ignored:
	 * '#', '№', ...
	 * 
	 * Tokenizer splits sentence on input and on search query.
	 * So later 2 arrays of words are compared.
	 * 
	 * Unnecessary split of 'NC-42', '2-B' '63/28' (house number) causes 
	 * unnecessary complication and computation.
	 * 
	 * It's important to not split what very likely will be combined.
	 * For example 'NC-42' == 'NC42', '2-B' == '2B'.
	 * 
	 * However algorithm should support match and search for split words:
	 * Data: '2-nd street ', Search 'Street 2', 'Street #2', Street 2-nd'
	 * Data: 'NC 42', Search: 'NC-42', 'NC 42'.'
	 *
	 * Index indexes all words except Partial Numbers and some Common.
	 * So index could have: 'NC-42', 'MC20', '2-nd' (2 letters)
	 * But not stores: '63/28', '2B', 'B2', 
	 * --------------------------------
	 * Potential issue:
	 * 1. '2nd street' is indexed as '2nd' and not 'street.
	 * 	  Limitation: user *must* input 2nd as part of search.
	 *    Input won't work: 'street 2' (commmon words street, 2).
	 * 2. Data 'NC 42', ok indexed under 'NC'.
	 *    Query: 'NC-42' will find 'NC' and should match 'NC 42'
	 *    Other combinations needs to be checked.
	 * --------------------------------
	 * 
	 * 1. 'NC-42', 'NC42', 'NC 42' - 1 token (not searched by number
	 * 2. Number + suffix - searched by number
	 * 	   2-nd, 35 bis [35-bis, 35bis] - Missaglia
	 * 3. 'Friedrich Wilhelm Weber strasse' (Munster) - many tokens
	 *     Friedrich-Wilhelm-Weber-Straße, Hemauerstraße
	 *   - Autocorrect -  Weberstrasse -> Weber strasse
	 * ..........
	 * Matrix comparision - [Data #1 vs Input #2]...?
	 * 
	 * 
	 */

	public static void main(String[] args) throws IOException, InterruptedException {
//		SpatialTextSearchSettings.DEDUPLICATE_RES = true;
//		SpatialTextSearchSettings.SEARCH_BUILDINGS = false;
		File folder = new File("/Users/victorshcherb/osmand/maps/");
		String pattern = "Germany_b";
		String pattern2 = ".....";
		String query = "Berlin hauptstrasse"; // slow
		query = "Kelterstraße Kernen im Remstal";
		query = "Germany Kelter. Kernen im Remstal";

		// Building time vs no building
//		Search Stats 778.5 ms - read 754.6 ms atoms (tokens 442.4 ms, obj 1.8 ms), match 281.5 ms, comp 26.4 ms
//		Search Stats 925.5 ms - read 799.8 ms atoms (tokens 442.5 ms, obj 16.3 ms), match 280.5 ms, comp 149.5 ms
		
		pattern = "Us_";
//		pattern = "Map";
		query = "Salt Lake City Pennsylvania Place 123 UT USA";
		query = "Salt Lake City Elephant";
//		query = "Salt Lake City Lake";
//		query = "Salt Lake City Pennsylvania Street";
//		query = "West Valley City";
//		query = "USA Salt Lake City Pennsylvania Street 41";
//		query = "Pennsylvania Avenue Pennsylvania USA"; // 31372516
//		query = "Pennsylvania Avenue Philadelphia Pennsylvania USA"; 
//		query = "Pennsylvania Avenue Philadelphia Philadelphia County Pennsylvania USA";
//		query = "Pennsylvania Avenue White Oak Allegheny County Pennsylvania USA"; // 11947214
//		query ="Township";

//		pattern = "Liechtenstein_europe.obf";
//		query = "Vaduz Lettstrasse";
//		query = "Vaduz ";
//		query = "Jugendheim Malbun";

//		pattern = "Netherlands_";
//		query = "1186RZ Logger 324D Amstelveen";
//		query = "Farm";
		
		pattern = "Turkey_";
//		query = "Sokak 23018. Balikesir"; // no results?
//		query = "2301. Sokak"; // Test 23018., 23018 - Fixed NameIndexCreator - parsePureIntegerSuffix
		query = "Sokak 23018."; // Test calle 2
		
//		pattern = "regions.ocbf" ;
		
//		pattern = "Ukraine_kyiv-city_europe.obf";
//		pattern = "Map";
//		query = "Kyiv Глушкова 1"; // vs 'Kyiv 1'
//		query = "нова пошта Бульварно Кудрявська";
//		query = "Бульварно-кудрявс.";
//		query = "Ukraine kyiv saks.";
//		query = "пузата хата mcdonal.";
//		query = "Нова пошта 3 харків";
//		query = "2-га Нова вулиця"; // unit test
//		query = "2 Нова вулиця"; // unit test
//		query = "саксаг. 63/28"; // 129-Б, 63/28??, 63-28+ // -саксаг. 63 28
//		query = "саксаг. 63/28, 2";
//		query = "саксаг. 63/28 подъезд 2";
//		query = "Яр. вал 29-г";
//		query = "25 Школа володимирська вулиця"; // ALWAYS_READ_COMMON_WORDS_ATOMS = true or show category (centre ?) ! 
//		query = "андріівський узвіз Школа "; // ALWAYS_READ_COMMON_WORDS_ATOMS = true
//		query = "Школа А+";
//		query = "школа №25"; // test '№25', '25'? -- 'школа', 'школа №25', 'школа 25'
//		query = "ВЕЛОwatt";
//		query = "O128894."; // FIX Osm id getOsmIdFromMapObjectId
		
		pattern = "Australia";
		query = "Holmby road 18 B"; // 'Holmby 18 B', 'Holmby 18-B', 'Holmby 18B'
//		query = "Holmby Melbourne 18B";
		
//		pattern = "World_basemap_2";
//		pattern2 = "Ukraine";
//		query = "о. Пасхи"; // o. -> остров
//		query = "New york";
//		query  = "Madeira"; // short_name	Madeira
//		query  = "Everest";
//		query  = "Rio de Janeiro";

		//		pattern = "Spain_aragon_europe_";
//		query = "Basílica de Nuestra Señora del Pilar";
//		query = "Catedral-Basílica de Nuestra Señora del Pilar"; // 7 words! 2^7 combinations
		
//		pattern = "Peru_"; 
//		query ="Calle 20 188 San Isidro Lima";
//		query ="Lima Calle 20 San Isidro";
//		query ="Calle 20 ";

		long t = System.nanoTime();

		List<BinaryMapIndexReader> ls = new ArrayList<BinaryMapIndexReader>();
		for (File f : folder.listFiles()) {
			if (f.getName().startsWith(pattern) || f.getName().startsWith(pattern2)) {
				SpatialTextSearch.initFile(ls, f);
			} else if(f.getName().equals(OsmandRegions.REGIONS_OCBF)){
				SpatialTextSearch.initFile(ls, f);
			}
		}
		SpatialTextSearch a = new SpatialTextSearch();
		System.out.println(String.format("Index files %.1f ms", (System.nanoTime() - t) / 1e6));

		SpatialSearchContext searchContext = new SpatialSearchContext(ls, null);
		SpatialSearchResults rs = a.searchTest(query, searchContext);
		SpatialSearchResult mainResult = rs.getFirstResult();
		if (mainResult == null || (mainResult.matchedTokens() < rs.tokens.size() - 2)) {
			// another way to check to check to get mainResult - boundary object
			City bbox = null;
			for(MapObject o : mainResult.getObjects()) {
				if(o instanceof City c && c.getBbox31() != null) {
					// check that city is not inside maps searched
					bbox = c;
					break;
				}
			}
			if (bbox != null) {
				System.out.println("Search other region - " + bbox);
			}
		}
		
		searchContext = new SpatialSearchContext(ls, null);
		SpatialTextSearchSettings.ALWAYS_READ_COMMON_WORDS_ATOMS = true;
		a.searchTest(query, searchContext);
	}
}
