package net.osmand.plus.settings.coordinates

import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.settings.backend.OsmandSettings
import java.util.Collections
import java.util.LinkedHashSet

class CoordinateFormatPreferences(private val settings: OsmandSettings) {

	fun getPreferredIds(): List<String> {
		return getPreferredIds(settings.applicationMode)
	}

	fun getPreferredIds(mode: ApplicationMode): List<String> {
		return sanitizePreferredIds(settings.PREFERRED_COORDINATE_FORMAT_IDS.getStringsListForProfile(mode))
	}

	fun setPreferredIds(mode: ApplicationMode, ids: List<String>): Boolean {
		return settings.PREFERRED_COORDINATE_FORMAT_IDS.setModeValues(mode, sanitizePreferredIds(ids))
	}

	fun getPrimaryId(mode: ApplicationMode): String {
		return getPreferredIds(mode).first()
	}

	fun resetPreferredIds(mode: ApplicationMode): Boolean {
		return settings.PREFERRED_COORDINATE_FORMAT_IDS.setModeValues(mode, CoordinateFormatIds.DEFAULT_FORMAT_IDS)
	}

	fun copyPreferredIds(fromMode: ApplicationMode, toMode: ApplicationMode): Boolean {
		return setPreferredIds(toMode, getPreferredIds(fromMode))
	}

	fun addPreferredId(mode: ApplicationMode, id: String): Boolean {
		val normalized = CoordinateFormatIds.normalize(id) ?: return false
		val ids = ArrayList(getPreferredIds(mode))
		if (normalized in ids) {
			return false
		}
		ids.add(normalized)
		return setPreferredIds(mode, ids)
	}

	fun getRecentIds(): List<String> {
		return sanitizeIds(
			settings.RECENTLY_ADDED_COORDINATE_FORMAT_IDS.stringsList,
			emptyList(),
			MAX_RECENT_FORMAT_IDS
		)
	}

	fun addRecentId(id: String): Boolean {
		val normalized = CoordinateFormatIds.normalize(id) ?: return false
		val ids = ArrayList(getRecentIds())
		ids.remove(normalized)
		ids.add(0, normalized)
		while (ids.size > MAX_RECENT_FORMAT_IDS) {
			ids.removeAt(ids.lastIndex)
		}
		return settings.RECENTLY_ADDED_COORDINATE_FORMAT_IDS.setModeValues(settings.applicationMode, ids)
	}

	fun migrateFromLegacyFormat(mode: ApplicationMode, legacyFormat: Int): Boolean {
		if (settings.PREFERRED_COORDINATE_FORMAT_IDS.isSetForMode(mode)) {
			return false
		}
		return settings.PREFERRED_COORDINATE_FORMAT_IDS.setModeValues(mode, getLegacyPreferredIds(legacyFormat))
	}

	private fun sanitizePreferredIds(ids: List<String>?): List<String> {
		return sanitizeIds(ids, CoordinateFormatIds.DEFAULT_FORMAT_IDS)
	}

	companion object {
		const val MAX_RECENT_FORMAT_IDS = 5

		@JvmStatic
		fun getLegacyPreferredIds(legacyFormat: Int): List<String> {
			val ids = LinkedHashSet<String>()
			CoordinateFormatIds.fromOldFormat(legacyFormat)?.let { ids.add(it) }
			ids.addAll(CoordinateFormatIds.ALL_BUILT_IN_FORMAT_IDS)
			return immutableCopy(ids)
		}

		private fun sanitizeIds(
			ids: List<String>?,
			fallbackIds: List<String>,
			maxCount: Int = Int.MAX_VALUE
		): List<String> {
			val sanitized = LinkedHashSet<String>()
			ids?.forEach { id ->
				CoordinateFormatIds.normalize(id)?.let { normalized ->
					if (sanitized.size < maxCount) {
						sanitized.add(normalized)
					}
				}
			}
			if (sanitized.isEmpty() && fallbackIds.isNotEmpty()) {
				sanitized.addAll(fallbackIds)
			}
			return immutableCopy(sanitized)
		}

		private fun immutableCopy(ids: Collection<String>): List<String> {
			return Collections.unmodifiableList(ArrayList(ids))
		}
	}
}
