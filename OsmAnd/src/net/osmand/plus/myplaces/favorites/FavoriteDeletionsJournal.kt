package net.osmand.plus.myplaces.favorites

import net.osmand.PlatformUtil
import net.osmand.data.FavouritePoint
import net.osmand.plus.OsmandApplication
import net.osmand.plus.myplaces.favorites.FavouritesFileHelper.FAV_FILE_PREFIX
import net.osmand.shared.IndexConstants.TMP_FILE_EXT
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

object FavoriteDeletionsJournal {

	private val log = PlatformUtil.getLog(FavoriteDeletionsJournal::class.java)

	private const val PENDING_DELETIONS_SUFFIX = "_deletions"
	private const val PREFIX_POINT = "point:"
	private const val PREFIX_GROUP = "group:"

	private val lock = Any()

	private fun getFile(app: OsmandApplication): File {
		return app.getFileStreamPath(FAV_FILE_PREFIX + PENDING_DELETIONS_SUFFIX + TMP_FILE_EXT)
	}

	@JvmStatic
	fun addPoint(
		app: OsmandApplication,
		point: FavouritePoint?
	) {
		if (point != null) {
			addAll(app, listOf(point), null)
		}
	}

	@JvmStatic
	fun addGroup(
		app: OsmandApplication,
		group: FavoriteGroup?
	) {
		if (group != null) {
			addAll(app, null, listOf(group))
		}
	}

	@JvmStatic
	fun addAll(
		app: OsmandApplication,
		points: Collection<FavouritePoint>?,
		groups: Collection<FavoriteGroup>?
	) {
		val lines = buildList {
			points?.forEach { add(serializePoint(it.key)) }
			groups?.forEach { add(serializeGroup(it.name)) }
		}

		if (lines.isNotEmpty()) {
			appendPendingDeletionLines(app, lines)
		}
	}

	@JvmStatic
	fun read(app: OsmandApplication): ReadResult {
		synchronized(lock) {
			val file = getFile(app)
			val deletions = FavoritePendingDeletions()

			if (file.exists()) {
				try {
					file.bufferedReader(Charsets.UTF_8, 8192).useLines { lines ->
						lines.forEach { line ->
							if (line.isNotEmpty()) {
								deserializeLine(line, deletions)
							}
						}
					}
				} catch (e: IOException) {
					log.error("Failed to read favorite deletions journal", e)
					return ReadResult(
						deletions = deletions,
						state = null,
						readFailed = true
					)
				}
			}

			return ReadResult(
				deletions = deletions,
				state = getState(file),
				readFailed = false
			)
		}
	}

	@JvmStatic
	fun clearIfUnchanged(app: OsmandApplication, expectedState: JournalState): Boolean {
		synchronized(lock) {
			val file = getFile(app)
			val currentState = getState(file)

			if (currentState != expectedState) {
				return false
			}

			if (file.exists() && !file.delete()) {
				log.warn("Failed to clear favorite deletions journal: ${file.absolutePath}")
				return false
			}
			return true
		}
	}

	private fun appendPendingDeletionLines(app: OsmandApplication, lines: Collection<String>) {
		synchronized(lock) {
			val file = getFile(app)

			try {
				FileOutputStream(file, true).use { fos ->
					BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8), 8192).use { writer ->
						for (line in lines) {
							writer.write(line)
							writer.newLine()
						}
						writer.flush()
					}
				}
			} catch (e: IOException) {
				log.error("appendPendingDeletionLines failed", e)
			}
		}
	}

	private fun serializePoint(pointKey: String): String {
		return "$PREFIX_POINT$pointKey"
	}

	private fun serializeGroup(groupName: String): String {
		return "$PREFIX_GROUP$groupName"
	}

	private fun deserializeLine(line: String, deletions: FavoritePendingDeletions) {
		when {
			line.startsWith(PREFIX_POINT) -> {
				deletions.addPoint(line.removePrefix(PREFIX_POINT))
			}

			line.startsWith(PREFIX_GROUP) -> {
				deletions.addGroup(line.removePrefix(PREFIX_GROUP))
			}
		}
	}

	private fun getState(file: File): JournalState {
		return if (file.exists()) {
			JournalState(
				length = file.length(),
				timestamp = file.lastModified()
			)
		} else {
			JournalState(
				length = 0L,
				timestamp = 0L
			)
		}
	}

	data class ReadResult(
		val deletions: FavoritePendingDeletions,
		val state: JournalState?,
		val readFailed: Boolean
	)

	data class JournalState(
		val length: Long,
		val timestamp: Long
	)
}