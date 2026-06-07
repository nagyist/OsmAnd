package net.osmand.plus.gallery.contract

import androidx.fragment.app.FragmentActivity
import net.osmand.plus.gallery.model.GalleryItem

interface IGalleryGridController : IGalleryListener, IGalleryActionListener {
	fun attach(view: IGalleryGridView)
	fun detach()
	fun onScreenDestroyed(activity: FragmentActivity?)
	fun getScreenTitle(): String?
	fun getGalleryItems(): List<GalleryItem>
	fun getSpanCount(isPortrait: Boolean): Int

	fun onScaleBegin()
	fun onScaleEnd()
	fun onScaleChanged(scaleFactor: Float): Boolean
}
