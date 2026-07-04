package net.osmand.plus.myplaces.favorites

import net.osmand.util.Algorithms
import java.util.concurrent.CountDownLatch

class SaveBatch(
	groups: List<FavoriteGroup>,
	var saveAllGroups: Boolean,
	listener: FavoritesListener?,
	waiter: CountDownLatch?
) {

	var groups: MutableList<FavoriteGroup> = ArrayList(groups)
		private set

	val listeners: MutableList<FavoritesListener> = ArrayList()
	val waiters: MutableList<CountDownLatch> = ArrayList()

	init {
		addRequest(listener, waiter)
	}

	fun merge(
		groups: List<FavoriteGroup>,
		saveAllGroups: Boolean,
		listener: FavoritesListener?,
		waiter: CountDownLatch?
	) {
		if (saveAllGroups) {
			this.groups = ArrayList(groups)
			this.saveAllGroups = true
		} else {
			mergeGroups(this.groups, groups)
		}
		addRequest(listener, waiter)
	}

	private fun addRequest(listener: FavoritesListener?, waiter: CountDownLatch?) {
		if (listener != null) {
			listeners.add(listener)
		}
		if (waiter != null) {
			waiters.add(waiter)
		}
	}

	private companion object {

		fun mergeGroups(destination: MutableList<FavoriteGroup>, source: List<FavoriteGroup>) {
			for (sourceGroup in source) {
				var replaced = false
				for (i in destination.indices) {
					if (Algorithms.stringsEqual(destination[i].name, sourceGroup.name)) {
						destination[i] = sourceGroup
						replaced = true
						break
					}
				}
				if (!replaced) {
					destination.add(sourceGroup)
				}
			}
		}
	}
}
