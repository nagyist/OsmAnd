package net.osmand.plus.gallery.controller

import android.view.View
import androidx.fragment.app.FragmentActivity
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.base.dialog.BaseDialogController
import net.osmand.plus.gallery.attached.helpers.AttachedMediaUiHelper
import net.osmand.plus.gallery.contract.IGalleryGridController
import net.osmand.plus.gallery.contract.IGalleryGridView
import net.osmand.plus.gallery.data.GalleryKey
import net.osmand.plus.gallery.model.GalleryAction
import net.osmand.plus.gallery.model.GalleryItem
import net.osmand.plus.gallery.ui.GalleryGridAdapter
import net.osmand.plus.gallery.ui.GalleryGridItemDecorator.Companion.GRID_SCREEN_ITEM_SPACE_DP
import net.osmand.plus.gallery.ui.GalleryGridSettings
import net.osmand.plus.gallery.ui.holders.MediaHolderType
import net.osmand.plus.utils.AndroidUtils
import net.osmand.shared.media.domain.MediaItem
import net.osmand.shared.media.domain.MediaOrigin
import net.osmand.shared.media.domain.MediaType

abstract class GalleryGridController(
	app: OsmandApplication,
	open val key: GalleryKey
) : BaseDialogController(app), IGalleryGridController {

	protected var view: IGalleryGridView? = null

	private var newScaleFactor = 0f
	private var zoomedForPinch = false

	private val standardPhotoSizePx =
		app.resources.getDimensionPixelSize(R.dimen.gallery_standard_icon_size)

	override fun attach(view: IGalleryGridView) {
		this.view = view
	}

	override fun detach() {
		this.view = null
	}

	override fun onScreenDestroyed(activity: FragmentActivity?) {
		detach()
		finishProcessIfNeeded(activity)
	}

	abstract override fun getScreenTitle(): String?

	override fun getGalleryItems(): List<GalleryItem> {
		val items = mutableListOf<GalleryItem>()
		val mediaItems = app.galleryHelper.repository.get(key)
			?.getItems()
			?.map { toGalleryItem(it) }
			?: emptyList()

		if (mediaItems.isNotEmpty()) {
			items.add(GalleryItem.MediaCount)
		}
		items.addAll(mediaItems)
		return items
	}

	override fun getSpanCount(isPortrait: Boolean): Int {
		return GalleryGridSettings.getSpanCount(app, isPortrait)
	}

	private fun setSpanCount(isPortrait: Boolean, count: Int) {
		GalleryGridSettings.setSpanCount(app, isPortrait, count)
	}

	// --- Image size ---

	fun resolveSpanResizableSize(viewWidth: Int?): Int {
		val mapActivity = view?.getMapActivity() ?: return standardPhotoSizePx
		val isPortrait = view?.isPortrait() ?: return standardPhotoSizePx

		val spanCount = getSpanCount(isPortrait)
		val padding = AndroidUtils.dpToPx(app, 13f)
		val itemSpace = AndroidUtils.dpToPx(app, GRID_SCREEN_ITEM_SPACE_DP * 2f)
		val screenWidth = viewWidth ?: if (isPortrait) {
			AndroidUtils.getScreenWidth(mapActivity)
		} else {
			AndroidUtils.getScreenHeight(mapActivity)
		}
		val spaceForItems = screenWidth - (padding * 2) - (spanCount * itemSpace)
		return spaceForItems / spanCount
	}

	// --- Scale ---

	override fun onScaleBegin() {
		newScaleFactor = 0f
	}

	override fun onScaleEnd() {
		newScaleFactor = 0f
		zoomedForPinch = false
	}

	override fun onScaleChanged(scaleFactor: Float): Boolean {
		if (zoomedForPinch) return false

		newScaleFactor += if (scaleFactor < 1f) {
			-(scaleFactor - 1f) * SCALE_MULTIPLIER
		} else {
			(1f - scaleFactor) * SCALE_MULTIPLIER
		}

		val isPortrait = view?.isPortrait() ?: return false
		val previousCount = getSpanCount(isPortrait)
		val newCount = newScaleFactor.toInt() + previousCount

		if (newCount != previousCount && newCount in MIN_SPAN_COUNT..MAX_SPAN_COUNT) {
			newScaleFactor = 0f
			setSpanCount(isPortrait, newCount)
			zoomedForPinch = true
			view?.updateSpan()
			return true
		}
		return false
	}

	// --- Media ---

	override fun onMediaItemClicked(mediaItem: MediaItem) {
		val activity = view?.getMapActivity() ?: return
		val nightMode = view?.isNightMode() ?: false
		if (mediaItem.type == MediaType.PHOTO) {
			GalleryPagerController.show(activity, key, mediaItem.id)
		} else {
			AttachedMediaUiHelper(activity).openMediaItem(mediaItem, nightMode)
		}
	}

	override fun handleGalleryAction(v: View, action: GalleryAction) {}

	private fun toGalleryItem(mediaItem: MediaItem): GalleryItem.Media {
		return GalleryItem.Media(
			mediaItem = mediaItem,
			showLoadingProgress = mediaItem.origin == MediaOrigin.OTHER
		)
	}

	fun createAdapter(mapActivity: MapActivity, viewWidth: Int?, nightMode: Boolean): GalleryGridAdapter {
		val registry = app.galleryHelper.loadStateRegistry
		return GalleryGridAdapter(
			mapActivity = mapActivity,
			onMediaClicked = ::onMediaItemClicked,
			onReloadMediaItems = ::onReloadMediaItems,
			onActionClicked = ::handleGalleryAction,
			mediaHolderType = { MediaHolderType.SPAN_RESIZABLE },
			resolveResizableImageSize = { resolveSpanResizableSize(viewWidth) },
			isLoadFailed = registry::isFailed,
			onLoadFailed = registry::markFailed,
			nightMode = nightMode
		)
	}

	companion object {
		const val MIN_SPAN_COUNT = 2
		const val MAX_SPAN_COUNT = 7
		const val SCALE_MULTIPLIER = 3f
	}
}