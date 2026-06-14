package net.osmand.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.osmand.binary.CommonWords;
import net.osmand.util.SearchAlgorithms.SuffixDictionary;
import net.osmand.util.SearchAlgorithms.SuffixEntry;

/**
 * Basic algorithms that are used in Search
 */
public class SearchIndexPrepareAlgorithms {
	public static class CompactSuffixes {
		public final List<Integer> suffixesBitsetIndex = new ArrayList<>();
		public String extraSuffix;
		public int nonCommonWords;
	}

	public static class CommonIndexedTokens {
		public final List<String> values = new ArrayList<>();
		public final List<Integer> matched = new ArrayList<>();
		public final List<Integer> nonindexed = new ArrayList<>();
		public final Map<String, Integer> tokenToIndex = new HashMap<>();
	}

	public static class CompactSuffixDictionary<T> {
		public static final int MAX_DICTIONARY_SIZE = 128;
		public final List<SuffixEntry> dictionaryEntries = new ArrayList<>();
		public final Map<String, Integer> resolvedSuffixToIndex = new HashMap<>();
		public final Map<T, CompactSuffixes> suffixes = new LinkedHashMap<>();
	}

	public static class CombinedSuffixDictionary<T> {
		public final List<SuffixEntry> dictionaryEntries = new ArrayList<>();
		public final List<Integer> commonDictionaryEntries = new ArrayList<>();
		public final Map<String, Integer> resolvedSuffixToIndex = new HashMap<>();
		public final Map<T, int[]> bitsets = new LinkedHashMap<>();
		public final Map<T, CompactSuffixes> compactSuffixes = new LinkedHashMap<>();
	}

	private record CommonSuffixCandidate(String fullToken) {
	}

	private record CodePointPrefixMatch(int leftOffset, int rightOffset, int commonPrefixCodePointLength) {
	}

    private static CodePointPrefixMatch startWith(String token, String prefix) {
        int leftOffset = 0;
        int rightOffset = 0;
        int commonPrefixCodePointLength = 0;
        while (leftOffset < token.length() && rightOffset < prefix.length()) {
            int leftCodePoint = token.codePointAt(leftOffset);
            int rightCodePoint = prefix.codePointAt(rightOffset);
            if (leftCodePoint != rightCodePoint) {
                break;
            }
            leftOffset += Character.charCount(leftCodePoint);
            rightOffset += Character.charCount(rightCodePoint);
            commonPrefixCodePointLength++;
        }
        return new CodePointPrefixMatch(leftOffset, rightOffset, commonPrefixCodePointLength);
    }

    private static int suffixOffsetAfterPrefix(String token, String prefix) {
        CodePointPrefixMatch prefixMatch = startWith(token, prefix);
        if (prefixMatch.rightOffset != prefix.length()) {
            return -1;
        }
        return prefixMatch.leftOffset < token.length() ? prefixMatch.leftOffset : -1;
    }
	
    
	public static <T> CommonIndexedTokens nameIndexBuildCommonIndexedTokens(
			Map<String, ? extends Collection<T>> objectsByPrefix, Function<T, Collection<String>> partialTokenSupplier,
			Function<T, Collection<String>> separatedTokenSupplier) {
		return nameIndexBuildCommonIndexedTokens(objectsByPrefix,
				(prefix, object) -> partialTokenSupplier.apply(object),
				(prefix, object) -> separatedTokenSupplier.apply(object));
	}

	public static <T> CommonIndexedTokens nameIndexBuildCommonIndexedTokens(
			Map<String, ? extends Collection<T>> objectsByPrefix,
			BiFunction<String, T, Collection<String>> partialTokenSupplier,
			BiFunction<String, T, Collection<String>> separatedTokenSupplier) {
		Map<String, Integer> matched = new HashMap<>();
		for (Map.Entry<String, ? extends Collection<T>> entry : objectsByPrefix.entrySet()) {
			String prefix = entry.getKey();
			for (T object : entry.getValue()) {
				Set<String> objectCommonTokens = new LinkedHashSet<>();
				for (CommonSuffixCandidate candidate : collectCommonSuffixCandidates(prefix, object,
						partialTokenSupplier, separatedTokenSupplier)) {
					objectCommonTokens.add(candidate.fullToken());
				}
				for (String token : objectCommonTokens) {
					matched.merge(token, 1, Integer::sum);
				}
			}
		}
		List<String> values = new ArrayList<>(matched.keySet());
		values.sort(SearchIndexPrepareAlgorithms::compareCommonIndexedTokens);
		CommonIndexedTokens data = new CommonIndexedTokens();
		for (String value : values) {
			data.tokenToIndex.put(value, data.values.size());
			data.values.add(value);
			data.matched.add(matched.get(value));
			data.nonindexed.add(0);
		}
		return data;
	}

	private static int compareCommonIndexedTokens(String left, String right) {
		int leftCommon = CommonWords.getCommon(left);
		int rightCommon = CommonWords.getCommon(right);
		boolean leftIsCommon = leftCommon != -1;
		boolean rightIsCommon = rightCommon != -1;
		if (leftIsCommon != rightIsCommon) {
			return leftIsCommon ? -1 : 1;
		}
		if (leftIsCommon && leftCommon != rightCommon) {
			return Integer.compare(leftCommon, rightCommon);
		}
		int leftFrequent = CommonWords.getFrequentlyUsed(left);
		int rightFrequent = CommonWords.getFrequentlyUsed(right);
		if (leftFrequent != rightFrequent) {
			return Integer.compare(leftFrequent, rightFrequent);
		}
		return left.compareTo(right);
	}

	private static boolean isCommonIndexedToken(String token) {
		return token != null && !isPureDecimalInteger(token)
				&& (CommonWords.getCommon(token) != -1 || CommonWords.getFrequentlyUsed(token) != -1);
	}

	private static <T> List<CommonSuffixCandidate> collectCommonSuffixCandidates(String prefix, T object,
			BiFunction<String, T, Collection<String>> partialTokenSupplier,
			BiFunction<String, T, Collection<String>> separatedTokenSupplier) {
		List<CommonSuffixCandidate> candidates = new ArrayList<>();
		for (String token : partialTokenSupplier.apply(prefix, object)) {
			int suffixOffset = suffixOffsetAfterPrefix(token, prefix);
			String suffix = null;
			String fullToken = null;
			if (suffixOffset < 0) {
				if (Objects.equals(token, prefix)) {
					suffix = "";
					fullToken = prefix;
				}
			} else {
				suffix = Normalizer.normalize(token.substring(suffixOffset), Normalizer.Form.NFC);
				fullToken = prefix + suffix;
			}
			if (isCommonIndexedToken(fullToken)) {
				candidates.add(new CommonSuffixCandidate(fullToken));
			}
		}
		for (String token : separatedTokenSupplier.apply(prefix, object)) {
			if (Objects.equals(token, prefix) || Algorithms.isEmpty(token) || encodePureDecimalSuffix(token) != null) {
				continue;
			}
			if (isCommonIndexedToken(token)) {
				candidates.add(new CommonSuffixCandidate(token));
			}
		}
		return candidates;
	}

	/**
	 * Builds one compact suffix dictionary for the new name-index structure.
	 * Partial token remainders are stored as-is; separated word-boundary suffixes
	 * are stored with a single leading space marker. The dictionary is capped at
	 * {@link CompactSuffixDictionary#MAX_DICTIONARY_SIZE}; over-cap suffixes spill
	 * into {@code extraSuffix}. Pure decimal separated suffixes use odd inline
	 * values and do not consume dictionary slots.
	 */
	public static <T> CombinedSuffixDictionary<T> nameIndexBuildCombinedSuffixDictionary(String prefix, List<T> objects,
			Function<T, Collection<String>> partialTokenSupplier,
			Function<T, Collection<String>> separatedTokenSupplier) {
		return nameIndexBuildCombinedSuffixDictionary(prefix, objects, partialTokenSupplier, separatedTokenSupplier,
				null);
	}

	public static <T> CombinedSuffixDictionary<T> nameIndexBuildCombinedSuffixDictionary(String prefix, List<T> objects,
			Function<T, Collection<String>> partialTokenSupplier,
			Function<T, Collection<String>> separatedTokenSupplier, CommonIndexedTokens commonTokens) {
		CombinedSuffixDictionary<T> data = new CombinedSuffixDictionary<>();
		Map<T, Set<String>> suffixesByObject = new LinkedHashMap<>();
		Map<T, Set<Integer>> commonRefsByObject = new LinkedHashMap<>();
		Map<String, Integer> suffixFrequency = new HashMap<>();
		Map<Integer, Integer> commonRefToIndex = new LinkedHashMap<>();

		for (T object : objects) {
			Set<String> objectSuffixes = new LinkedHashSet<>();
			Set<Integer> objectCommonRefs = new LinkedHashSet<>();
			Set<Integer> objectNonindexedCommonRefs = new LinkedHashSet<>();
			suffixesByObject.put(object, objectSuffixes);
			commonRefsByObject.put(object, objectCommonRefs);
			for (String token : partialTokenSupplier.apply(object)) {
				int suffixOffset = suffixOffsetAfterPrefix(token, prefix);
				String suffix = null;
				String fullToken = null;
				if (suffixOffset < 0) {
					if (Objects.equals(token, prefix)) {
						suffix = "";
						fullToken = prefix;
					}
				} else {
					suffix = Normalizer.normalize(token.substring(suffixOffset), Normalizer.Form.NFC);
					fullToken = prefix + suffix;
				}
				if (suffix != null) {
					// suffixesCommonDictionary stores separated suffix-token refs only;
					// partial/full-token
					// suffixes stay in the local dictionary or extraSuffix even when their full
					// token is common.
					objectSuffixes.add(suffix);
					Integer commonIndex = commonTokens == null ? null : commonTokens.tokenToIndex.get(fullToken);
					if (commonIndex != null) {
						objectNonindexedCommonRefs.add(commonIndex);
					}
				}
			}
			for (String token : separatedTokenSupplier.apply(object)) {
				if (Objects.equals(token, prefix) || Algorithms.isEmpty(token)
						|| encodePureDecimalSuffix(token) != null) {
					continue;
				}
				Integer commonIndex = commonTokens == null ? null : commonTokens.tokenToIndex.get(token);
				if (commonIndex == null) {
					objectSuffixes.add(" " + token);
				} else {
					objectCommonRefs.add(commonIndex);
					objectNonindexedCommonRefs.remove(commonIndex);
				}
			}
			if (commonTokens != null) {
				for (int commonIndex : objectNonindexedCommonRefs) {
					commonTokens.nonindexed.set(commonIndex, commonTokens.nonindexed.get(commonIndex) + 1);
				}
			}
			for (String suffix : objectSuffixes) {
				suffixFrequency.merge(suffix, 1, Integer::sum);
			}
			for (int commonRef : objectCommonRefs) {
				commonRefToIndex.computeIfAbsent(commonRef, ignored -> commonRefToIndex.size());
			}
		}
		List<String> rankedSuffixes = new ArrayList<>(suffixFrequency.keySet());
		rankedSuffixes.sort(Comparator.comparingInt((String suffix) -> suffixFrequency.get(suffix)).reversed()
				.thenComparing(Comparator.naturalOrder()));
		if (rankedSuffixes.size() > CompactSuffixDictionary.MAX_DICTIONARY_SIZE) {
			rankedSuffixes = rankedSuffixes.subList(0, CompactSuffixDictionary.MAX_DICTIONARY_SIZE);
		}
		Set<String> dictionarySuffixSet = new HashSet<>(rankedSuffixes);

		String previousSuffix = null;
		for (String suffix : rankedSuffixes) {
			String encodedSuffix = SearchAlgorithms.nameIndexEncodeSuffix(suffix, previousSuffix);
			SuffixEntry entry = new SuffixEntry(suffix, encodedSuffix);
			data.resolvedSuffixToIndex.put(entry.resolvedSuffix(), data.dictionaryEntries.size());
			data.dictionaryEntries.add(entry);
			previousSuffix = suffix;
		}
		data.commonDictionaryEntries.addAll(commonRefToIndex.keySet());

		for (T object : objects) {
			CompactSuffixes objectSuffixes = new CompactSuffixes();
			List<String> extraSuffixes = new ArrayList<>();
			for (String suffix : suffixesByObject.getOrDefault(object, Collections.emptySet())) {
				Integer suffixIndex = dictionarySuffixSet.contains(suffix) ? data.resolvedSuffixToIndex.get(suffix)
						: null;
				if (suffixIndex != null) {
					objectSuffixes.suffixesBitsetIndex.add(suffixIndex << 1);
					// Empty suffix only confirms that prefix itself is a complete token.
					if (!suffix.isEmpty()) {
						objectSuffixes.nonCommonWords++;
					}
				} else if (!suffix.isEmpty()) {
					// Empty suffix cannot be represented in space-delimited extraSuffix.
					extraSuffixes.add(suffix);
					objectSuffixes.nonCommonWords++;
				}
			}
			for (int commonRef : commonRefsByObject.getOrDefault(object, Collections.emptySet())) {
				Integer commonDictionaryIndex = commonRefToIndex.get(commonRef);
				if (commonDictionaryIndex != null) {
					objectSuffixes.suffixesBitsetIndex
							.add((data.dictionaryEntries.size() + commonDictionaryIndex) << 1);
				}
			}
			for (String token : new LinkedHashSet<>(separatedTokenSupplier.apply(object))) {
				if (!Objects.equals(token, prefix)) {
					Integer encodedNumber = encodePureDecimalSuffix(token);
					if (encodedNumber != null) {
						objectSuffixes.suffixesBitsetIndex.add(encodedNumber);
						objectSuffixes.nonCommonWords++;
					}
				}
			}
			Collections.sort(objectSuffixes.suffixesBitsetIndex);
			Collections.sort(extraSuffixes);
			if (!extraSuffixes.isEmpty()) {
				objectSuffixes.extraSuffix = String.join(" ", extraSuffixes);
			}
			data.compactSuffixes.put(object, objectSuffixes);
		}
		return data;
	}

	public static boolean isPureDecimalInteger(String token) {
		if (token == null || token.isEmpty()) {
			return false;
		}
		for (int i = 0; i < token.length(); i++) {
			if (!Character.isDigit(token.charAt(i))) {
				return false;
			}
		}
		return token.length() == 1 || token.charAt(0) != '0';
	}

	private static Integer encodePureDecimalSuffix(String token) {
		if (!isPureDecimalInteger(token)) {
			return null;
		}
		try {
			long value = Long.parseLong(token);
			if (value > 0x7fffffffL) {
				return null;
			}
			return (int) ((value << 1) | 1);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Cross-category suffix policy for compact complex tokens.
	 * <p>
	 * Current production policy is intentionally strict: - USUAL prefixes include
	 * NUMBER tokens only. - FREQUENT prefixes include other FREQUENT tokens, COMMON
	 * tokens, and NUMBER tokens.
	 * <p>
	 * After index-size/search-quality testing, cross-category suffixes can be
	 * enabled here without changing writer call sites: - Set
	 * INCLUDE_USUAL_SUFFIXES_FOR_USUAL_PREFIX to true to let USUAL prefixes also
	 * match other USUAL tokens. - Set INCLUDE_FREQUENT_SUFFIXES_FOR_USUAL_PREFIX to
	 * true to let USUAL prefixes also match FREQUENT tokens. - Set
	 * INCLUDE_COMMON_SUFFIXES_FOR_USUAL_PREFIX to true to let USUAL prefixes also
	 * match COMMON tokens. - Set INCLUDE_USUAL_SUFFIXES_FOR_FREQUENT_PREFIX to true
	 * to let FREQUENT prefixes also match USUAL tokens. - Set
	 * INCLUDE_FREQUENT_SUFFIXES_FOR_FREQUENT_PREFIX to false to test FREQUENT
	 * prefixes without other FREQUENT tokens.
	 * <p>
	 * Keep these switches false by default until OBF size, dictionary hit rate,
	 * extraSuffix growth, and false-positive search behavior are measured on
	 * representative maps. NUMBER suffixes are not controlled by these switches:
	 * they remain included for both USUAL and FREQUENT prefixes.
	 */
	private static final boolean INCLUDE_USUAL_SUFFIXES_FOR_USUAL_PREFIX = false;
	private static final boolean INCLUDE_FREQUENT_SUFFIXES_FOR_USUAL_PREFIX = true;
	private static final boolean INCLUDE_COMMON_SUFFIXES_FOR_USUAL_PREFIX = true;
	private static final boolean INCLUDE_USUAL_SUFFIXES_FOR_FREQUENT_PREFIX = false;
	private static final boolean INCLUDE_FREQUENT_SUFFIXES_FOR_FREQUENT_PREFIX = true;

	public static Set<String> nameIndexPrepareComplexPrefixes(List<String> tokens, boolean allowNumberPrefixes) {
		List<String> uniqueTokens = new ArrayList<>(new LinkedHashSet<>(tokens));
		List<String> usual = new ArrayList<>();
		List<String> frequent = new ArrayList<>();
		List<String> common = new ArrayList<>();
		List<String> numbers = new ArrayList<>();
		for (String token : uniqueTokens) {
			if (CommonWords.isNumber2Letters(token)) {
				numbers.add(token);
			} else if (CommonWords.getCommon(token) != -1) {
				common.add(token);
			} else if (CommonWords.getFrequentlyUsed(token) != -1) {
				frequent.add(token);
			} else {
				usual.add(token);
			}
		}
		LinkedHashSet<String> prefixes = new LinkedHashSet<>();
		if (!usual.isEmpty()) {
			prefixes.addAll(usual);
			prefixes.addAll(frequent);
		} else if (!frequent.isEmpty()) {
			prefixes.addAll(frequent);
		} else if (!common.isEmpty()) {
			prefixes.addAll(common);
		} else if (allowNumberPrefixes) {
			prefixes.addAll(numbers);
		}
		return prefixes;
	}

	public static List<String> nameIndexPrepareComplexSuffixes(List<String> tokens, String prefix) {
		List<String> uniqueTokens = new ArrayList<>(new LinkedHashSet<>(tokens));
		List<String> usual = new ArrayList<>();
		List<String> frequent = new ArrayList<>();
		List<String> common = new ArrayList<>();
		List<String> numbers = new ArrayList<>();
		for (String token : uniqueTokens) {
			if (CommonWords.isNumber2Letters(token)) {
				numbers.add(token);
			} else if (CommonWords.getCommon(token) != -1) {
				common.add(token);
			} else if (CommonWords.getFrequentlyUsed(token) != -1) {
				frequent.add(token);
			} else {
				usual.add(token);
			}
		}
		List<String> suffixes = new ArrayList<>();
		if (usual.contains(prefix)) {
			if (INCLUDE_USUAL_SUFFIXES_FOR_USUAL_PREFIX) {
				suffixes.addAll(usual);
			}
			if (INCLUDE_FREQUENT_SUFFIXES_FOR_USUAL_PREFIX) {
				suffixes.addAll(frequent);
			}
			if (INCLUDE_COMMON_SUFFIXES_FOR_USUAL_PREFIX) {
				suffixes.addAll(common);
			}
			suffixes.addAll(numbers);
		} else if (frequent.contains(prefix)) {
			if (INCLUDE_USUAL_SUFFIXES_FOR_FREQUENT_PREFIX) {
				suffixes.addAll(usual);
			}
			if (INCLUDE_FREQUENT_SUFFIXES_FOR_FREQUENT_PREFIX) {
				suffixes.addAll(frequent);
			}
			suffixes.addAll(common);
			suffixes.addAll(numbers);
		} else {
			suffixes.addAll(uniqueTokens);
		}
		suffixes.removeIf(token -> Objects.equals(token, prefix));
		return suffixes;
	}

	public static boolean nameIndexIsSingleAlmostNumberValue(String rawText, List<String> normalizedTokens) {
		if (Algorithms.isEmpty(rawText) || normalizedTokens == null || normalizedTokens.isEmpty()) {
			return false;
		}
		for (int i = 0; i < rawText.length(); i++) {
			if (Character.isWhitespace(rawText.charAt(i))) {
				return false;
			}
		}
		for (String token : normalizedTokens) {
			if (!CommonWords.isNumber2Letters(token)) {
				return false;
			}
		}
		return true;
	}
	
	private static String substringByCodePoints(String value, int codePointCount) {
        if (codePointCount <= 0 || value.isEmpty()) {
            return "";
        }
        int availableCodePointCount = value.codePointCount(0, value.length());
        if (codePointCount >= availableCodePointCount) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, codePointCount));
    }

    public static String nameIndexPreparePrefix(String token, int maxPrefixLength) {
        String normalizedToken = SearchAlgorithms.normalizeToken(token);
	    if (maxPrefixLength <= 0) {
		    return "";
	    }
        if (normalizedToken.codePointCount(0, normalizedToken.length()) > maxPrefixLength) {
	        return substringByCodePoints(normalizedToken, maxPrefixLength);
        }
        return normalizedToken;
    }
	
	   /**
     * Collects unique suffixes for the prefix, stores them once in sorted encoded form, and builds per-object bitsets.
     */
    public static <T> SuffixDictionary<T> nameIndexBuildSuffixDictionary(String prefix, List<T> objects,
                                                                         Function<T, Collection<String>> tokenSupplier) {
        SuffixDictionary<T> data = new SuffixDictionary<>();
        TreeSet<String> sortedSuffixes = new TreeSet<>();
        Map<T, Set<String>> suffixesByObject = new LinkedHashMap<>();
        for (T object : objects) {
            Set<String> objectSuffixes = new LinkedHashSet<>();
            suffixesByObject.put(object, objectSuffixes);
            for (String token : tokenSupplier.apply(object)) {
                int suffixOffset = suffixOffsetAfterPrefix(token, prefix);
                String suffix;
                if (suffixOffset < 0) {
                    if (!Objects.equals(token, prefix)) {
                        continue;
                    }
                    suffix = "";
                } else {
                    suffix = Normalizer.normalize(token.substring(suffixOffset), Normalizer.Form.NFC);
                }
                if (suffix == null) {
                    continue;
                }
                objectSuffixes.add(suffix);
                sortedSuffixes.add(suffix);
            }
        }
        String previousSuffix = null;
        for (String suffix : sortedSuffixes) {
            String encodedSuffix = SearchAlgorithms.nameIndexEncodeSuffix(suffix, previousSuffix);
            SuffixEntry entry = new SuffixEntry(suffix, encodedSuffix);
            data.resolvedSuffixToIndex.put(entry.resolvedSuffix(), data.dictionaryEntries.size());
            data.dictionaryEntries.add(entry);
            previousSuffix = suffix;
        }
        int dictionaryWordCount = (data.dictionaryEntries.size() + Integer.SIZE - 1) / Integer.SIZE;
        if (dictionaryWordCount == 0) {
            return data;
        }
        for (T object : objects) {
            int[] bitsetWords = new int[dictionaryWordCount];
            Set<String> objectSuffixes = suffixesByObject.get(object);
            if (objectSuffixes != null) {
                for (String suffix : objectSuffixes) {
                    Integer suffixIndex = data.resolvedSuffixToIndex.get(suffix);
                    if (suffixIndex == null) {
                        continue;
                    }
                    bitsetWords[suffixIndex >> 5] |= 1 << (suffixIndex & 31);
                }
            }
            data.bitsets.put(object, bitsetWords);
        }
        return data;
    }

}
