package net.osmand.plus.gallery.attached

import androidx.fragment.app.FragmentActivity
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.gallery.controller.GalleryGridController
import net.osmand.plus.gallery.data.GalleryKey
import net.osmand.plus.gallery.ui.GalleryGridFragment

class AttachedMediaGridController(
	app: OsmandApplication,
	override val key: GalleryKey
) : GalleryGridController(app, key) {

	override fun getProcessId(): String = processId(key)

	override fun getScreenTitle(): String =
		app.getString(R.string.shared_string_media)

	companion object {
		private const val PROCESS_ID = "gallery_grid_attached"

		fun processId(key: GalleryKey): String =
			"${PROCESS_ID}_${key}"

		fun show(activity: FragmentActivity, key: GalleryKey) {
			val app = activity.application as OsmandApplication
			val controller = AttachedMediaGridController(app, key)
			app.dialogManager.register(controller.processId, controller)
			GalleryGridFragment.showInstance(activity, controller.processId)
		}
	}
}