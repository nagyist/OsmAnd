package net.osmand.plus.settings.coordinates

import net.osmand.plus.OsmandApplication
import net.osmand.plus.settings.backend.ApplicationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CoordinateFormatHelper(private val app: OsmandApplication) {

	val transformer = EpsgCoordinateTransformer(app)
	val repository = EpsgCatalogRepository(app)
	val formatter = CoordinateFormatFormatter(app, transformer)

	private val searchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var searchJob: Job? = null

	fun searchFormats(query: String?, callback: CoordinateSearchCallback) {
		searchJob?.cancel()
		searchJob = searchScope.launch {
			val results = if (query.isNullOrBlank()) repository.listAll() else repository.search(query)
			if (isActive) {
				app.runInUIThread { callback.onResult(results) }
			}
		}
	}

	fun cancelSearch() {
		searchJob?.cancel()
		searchJob = null
	}

	fun resolveFormats(ids: List<String>): List<CoordinateFormat> {
		return ids.map { id -> BuiltInCoordinateFormat.resolve(app, id) ?: repository.resolveFormat(id) }
	}

	fun getFormatSummary(format: CoordinateFormat): String {
		format.epsgCode?.let { return "EPSG:$it" }
		val location = app.locationProvider.lastKnownLocation
		val lat = location?.latitude ?: EXAMPLE_LAT
		val lon = location?.longitude ?: EXAMPLE_LON
		return formatter.formatExample(format, lat, lon)
	}

	fun syncLegacyPrimary(mode: ApplicationMode, ids: List<String>) {
		if (ids.isEmpty()) {
			return
		}
		val legacyFormat = BuiltInCoordinateFormat.resolve(app, ids[0])?.legacyFormat ?: return
		app.settings.COORDINATES_FORMAT.setModeValue(mode, legacyFormat)
	}

	companion object {
		const val EXAMPLE_LAT = 50.43855
		const val EXAMPLE_LON = 30.50124
	}
}

fun interface CoordinateSearchCallback {
	fun onResult(results: List<CoordinateFormat>)
}
