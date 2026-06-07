package net.osmand.plus.gallery.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.gallery.model.GalleryAction
import net.osmand.plus.gallery.model.GalleryItem
import net.osmand.plus.gallery.ui.holders.ActionViewHolder
import net.osmand.plus.gallery.ui.holders.GalleryMediaViewHolder
import net.osmand.plus.gallery.ui.holders.MediaCountHolder
import net.osmand.plus.gallery.ui.holders.MediaHolderType
import net.osmand.plus.gallery.ui.holders.NoInternetHolder
import net.osmand.plus.gallery.ui.holders.NoMediaHolder
import net.osmand.plus.utils.UiUtilities
import net.osmand.shared.media.MediaProvider
import net.osmand.shared.media.domain.MediaItem

class GalleryGridAdapter(
	private val mapActivity: MapActivity,
	private val onMediaClicked: (MediaItem) -> Unit,
	private val onReloadMediaItems: () -> Unit,
	private val onActionClicked: (View, GalleryAction) -> Unit,
	private val mediaHolderType: (position: Int) -> MediaHolderType,
	private val resolveResizableImageSize: (() -> Int)? = null,
	private val isLoadFailed: (MediaItem) -> Boolean,
	private val onLoadFailed: (MediaItem) -> Unit,
	private val nightMode: Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	private val app: OsmandApplication = mapActivity.app
	private val themedInflater: LayoutInflater = UiUtilities.getInflater(mapActivity, nightMode)
	private val mediaProvider = MediaProvider(app)
	private val items = mutableListOf<GalleryItem>()

	private val mainPhotoSizePx = app.resources.getDimensionPixelSize(R.dimen.gallery_big_icon_size)
	private val standardPhotoSizePx = app.resources.getDimensionPixelSize(R.dimen.gallery_standard_icon_size)

	private var loadingImages = false

	fun setItems(newItems: List<GalleryItem>) {
		items.clear()
		items.addAll(newItems)
		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return when (viewType) {
			MAIN_MEDIA_TYPE, MEDIA_TYPE -> {
				val itemView = inflate(R.layout.gallery_card_item, parent)
				GalleryMediaViewHolder(
					app, itemView, onMediaClicked, isLoadFailed,
					onLoadFailed, mediaProvider
				)
			}
			ACTION_VIEW_TYPE -> {
				val itemView = inflate(R.layout.context_menu_card_gallery_action_view, parent)
				ActionViewHolder(itemView, onActionClicked)
			}
			NO_MEDIA_TYPE -> {
				NoMediaHolder(inflate(R.layout.no_image_card, parent), app, onActionClicked)
			}
			NO_INTERNET_TYPE -> {
				val itemView = inflate(R.layout.no_internet_card, parent)
				NoInternetHolder(itemView, app, onReloadMediaItems)
			}
			MEDIA_COUNT_TYPE -> {
				MediaCountHolder(inflate(R.layout.images_count_item, parent), app)
			}
			else -> throw IllegalArgumentException("Unsupported view type: $viewType")
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		val item = items[position]
		when {
			holder is GalleryMediaViewHolder && item is GalleryItem.Media -> {
				val holderType = mediaHolderType(position)
				val imageSizePx = when (holderType) {
					MediaHolderType.SPAN_RESIZABLE -> resolveResizableImageSize?.invoke() ?: standardPhotoSizePx
					MediaHolderType.MAIN -> mainPhotoSizePx
					else -> standardPhotoSizePx
				}
				holder.bindView(mapActivity, item, imageSizePx, holderType, nightMode)
			}
			holder is ActionViewHolder && item is GalleryItem.Action ->
				holder.bindView(nightMode, mapActivity, item)
			holder is NoMediaHolder && item is GalleryItem.NoMedia ->
				holder.bindView(item, nightMode)
			holder is NoInternetHolder && item is GalleryItem.NoInternet ->
				holder.bindView(nightMode, loadingImages)
			holder is MediaCountHolder && item is GalleryItem.MediaCount ->
				holder.bindView(getMediaItemsCount(), nightMode)
		}
	}

	override fun onBindViewHolder(
		holder: RecyclerView.ViewHolder,
		position: Int,
		payloads: MutableList<Any>
	) {
		if (payloads.isNotEmpty() && payloads[0] == UPDATE_PROGRESS_BAR_PAYLOAD_TYPE) {
			if (holder is NoInternetHolder) holder.updateProgressBar(loadingImages)
		} else {
			super.onBindViewHolder(holder, position, payloads)
		}
	}

	override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
		if (holder is GalleryMediaViewHolder) {
			holder.cancelLoadingImage()
		}
		super.onViewRecycled(holder)
	}

	fun onLoadingImages(loadingImages: Boolean) {
		this.loadingImages = loadingImages
		for (i in items.indices) {
			if (items[i] is GalleryItem.NoInternet) {
				notifyItemChanged(i, UPDATE_PROGRESS_BAR_PAYLOAD_TYPE)
			}
		}
	}

	fun getItem(position: Int) = items[position]

	fun isRegularMediaItemOnPosition(position: Int) = getItemViewType(position) == MEDIA_TYPE

	override fun getItemCount(): Int = items.size

	override fun getItemViewType(position: Int): Int = when (items[position]) {
		is GalleryItem.Media -> if (position == 0) MAIN_MEDIA_TYPE else MEDIA_TYPE
		is GalleryItem.Action -> ACTION_VIEW_TYPE
		is GalleryItem.NoMedia -> NO_MEDIA_TYPE
		is GalleryItem.NoInternet -> NO_INTERNET_TYPE
		is GalleryItem.MediaCount -> MEDIA_COUNT_TYPE
	}

	fun getAnimator(): RecyclerView.ItemAnimator = object : DefaultItemAnimator() {
		override fun canReuseUpdatedViewHolder(viewHolder: RecyclerView.ViewHolder) = true
	}

	private fun inflate(resourceId: Int, root: ViewGroup, attachToRoot: Boolean = false): View =
		themedInflater.inflate(resourceId, root, attachToRoot)

	private fun getMediaItemsCount(): Int = items.count { it is GalleryItem.Media }

	companion object {
		private const val MAIN_MEDIA_TYPE = 0
		private const val MEDIA_TYPE = 1
		private const val ACTION_VIEW_TYPE = 2
		private const val NO_INTERNET_TYPE = 3
		private const val MEDIA_COUNT_TYPE = 4
		private const val NO_MEDIA_TYPE = 5

		private const val UPDATE_PROGRESS_BAR_PAYLOAD_TYPE = 1
	}
}
