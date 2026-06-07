package net.osmand.plus.gallery.ui.holders

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.gallery.model.GalleryItem
import net.osmand.plus.helpers.AndroidUiHelper
import net.osmand.plus.utils.AndroidUtils
import net.osmand.plus.utils.ColorUtilities
import net.osmand.shared.media.MediaProvider
import net.osmand.shared.media.MediaUriResolver
import net.osmand.shared.media.domain.MediaItem
import net.osmand.shared.media.domain.MediaType
import net.osmand.shared.util.ImageLoadSource
import net.osmand.shared.util.ImageLoaderCallback
import net.osmand.shared.util.ImageRequestListener
import net.osmand.shared.util.LoadingImage

class GalleryMediaViewHolder(
	private val app: OsmandApplication,
	itemView: View,
	private val onMediaItemClicked: (MediaItem) -> Unit,
	private val isLoadFailed: (MediaItem) -> Boolean,
	private val onLoadFailed: (MediaItem) -> Unit,
	private val mediaProvider: MediaProvider
) : RecyclerView.ViewHolder(itemView) {

	private val ivImage: ImageView = itemView.findViewById(R.id.image)
	private val ivSourceType: ImageView = itemView.findViewById(R.id.source_type)
	private val ivLoadSourceType: ImageView = itemView.findViewById(R.id.load_source_type)

	private val tvUrl: TextView = itemView.findViewById(R.id.url)
	private val border: View = itemView.findViewById(R.id.card_outline)
	private val progressBar: ProgressBar = itemView.findViewById(R.id.progress)

	private val iconsCache = app.uiUtilities

	private var loadingImage: LoadingImage? = null

	private var mapActivity: MapActivity? = null
	private var nightMode: Boolean = false
	private var imageSizePx: Int = 0
	var holderType: MediaHolderType = MediaHolderType.STANDARD

	fun bindView(
		mapActivity: MapActivity,
		galleryItem: GalleryItem.Media,
		imageSizePx: Int,
		holderType: MediaHolderType,
		nightMode: Boolean
	) {
		this.mapActivity = mapActivity
		this.nightMode = nightMode
		this.imageSizePx = imageSizePx
		this.holderType = holderType

		val mediaItem = galleryItem.mediaItem
		cancelLoadingImage()
		setupView(imageSizePx, nightMode)

		val iconName = mediaItem.origin.iconName
		val topIconId = AndroidUtils.getDrawableId(app, iconName)

		if (holderType == MediaHolderType.MAIN && topIconId != 0) {
			setSourceTypeIcon(iconsCache.getIcon(topIconId))
		} else {
			setSourceTypeIcon(null)
		}

		AndroidUtils.setBackground(mapActivity, border, getBackgroundId(nightMode))
		progressBar.visibility = if (galleryItem.showLoadingProgress) View.VISIBLE else View.GONE
		ivImage.setImageDrawable(null)
		ivImage.setOnClickListener(null)
		ivLoadSourceType.visibility = View.GONE

		if (isLoadFailed(mediaItem)) {
			bindUrl(mediaItem)
		} else {
			tryLoadImage(mediaItem)
		}
	}

	private fun tryLoadImage(mediaItem: MediaItem) {
		if (mediaItem.type != MediaType.PHOTO) {
			bindMediaIcon(mediaItem)
			return
		}

		loadingImage = mediaProvider.loadStandardSizeImage(mediaItem, object : ImageLoaderCallback {
			override fun onStart(bitmap: Bitmap?) {}

			override fun onSuccess(bitmap: Bitmap) {
				bindImage(mediaItem)
				ivImage.setImageDrawable(BitmapDrawable(ivImage.resources, bitmap))
			}

			override fun onError() {
				if (!app.settings.isInternetConnectionAvailable) {
					tryLoadCacheHiResImage(mediaItem)
				} else {
					onLoadFailed(mediaItem)
					bindUrl(mediaItem)
				}
			}
		}, object : ImageRequestListener {
			override fun onSuccess(source: ImageLoadSource) {
				updateLoadSource(source)
			}
		}, imageSizePx)
	}

	private fun tryLoadCacheHiResImage(mediaItem: MediaItem) {
		loadingImage = mediaProvider.loadFullSizeImage(mediaItem, object : ImageLoaderCallback {
			override fun onStart(bitmap: Bitmap?) {}

			override fun onSuccess(bitmap: Bitmap) {
				bindImage(mediaItem)
				ivImage.setImageDrawable(BitmapDrawable(ivImage.resources, bitmap))
			}

			override fun onError() {
				onLoadFailed(mediaItem)
				bindUrl(mediaItem)
			}
		}, object : ImageRequestListener {
			override fun onSuccess(source: ImageLoadSource) {
				updateLoadSource(source)
			}
		}, imageSizePx)
	}

	private fun updateLoadSource(source: ImageLoadSource?) {
		if (!app.settings.isInternetConnectionAvailable && ImageLoadSource.NETWORK != source) {
			ivLoadSourceType.visibility = View.VISIBLE
		} else {
			ivLoadSourceType.visibility = View.GONE
		}
	}

	private fun bindImage(mediaItem: MediaItem) {
		val layoutParams = FrameLayout.LayoutParams(
			FrameLayout.LayoutParams.MATCH_PARENT,
			FrameLayout.LayoutParams.MATCH_PARENT
		)
		layoutParams.gravity = Gravity.CENTER
		ivImage.visibility = View.VISIBLE
		ivImage.layoutParams = layoutParams
		ivImage.scaleType = ImageView.ScaleType.CENTER_CROP
		ivImage.setOnClickListener { onMediaItemClicked(mediaItem) }

		tvUrl.visibility = View.GONE
		border.visibility = View.GONE
		progressBar.visibility = View.GONE
	}

	private fun bindMediaIcon(mediaItem: MediaItem) {
		val iconId = when (mediaItem.type) {
			MediaType.VIDEO -> R.drawable.ic_action_video_dark
			MediaType.AUDIO -> R.drawable.ic_action_micro_dark
			else -> R.drawable.ic_action_image_disabled
		}
		ivImage.visibility = View.VISIBLE
		ivImage.setImageDrawable(iconsCache.getIcon(iconId))
		ivImage.scaleType = ImageView.ScaleType.CENTER
		ivImage.setOnClickListener { onMediaItemClicked(mediaItem) }

		tvUrl.visibility = View.GONE
		border.visibility = View.VISIBLE
		progressBar.visibility = View.GONE
		updateLoadSource(null)
		setSourceTypeIcon(null)
	}

	private fun bindUrl(mediaItem: MediaItem) {
		ivImage.visibility = View.GONE
		tvUrl.visibility = View.VISIBLE

		val displayUri = MediaUriResolver.getFailedLoadDisplayUri(mediaItem)
		if (displayUri != null) {
			tvUrl.text = displayUri
			tvUrl.setOnClickListener {
				mapActivity?.let { AndroidUtils.openUrl(it, displayUri, nightMode) }
			}
		}

		border.visibility = View.VISIBLE
		progressBar.visibility = View.GONE
		updateLoadSource(null)
		setSourceTypeIcon(null)
	}

	private fun setSourceTypeIcon(icon: android.graphics.drawable.Drawable?) {
		AndroidUiHelper.updateVisibility(ivSourceType, icon != null)
		ivSourceType.setImageDrawable(icon)
	}

	fun cancelLoadingImage() {
		loadingImage?.cancel()
		loadingImage = null
	}

	private fun setupView(sizeInPx: Int, nightMode: Boolean) {
		val layoutParams = FrameLayout.LayoutParams(sizeInPx, sizeInPx)
		itemView.layoutParams = layoutParams
		itemView.setBackgroundColor(ColorUtilities.getActivityBgColor(app, nightMode))
	}

	private fun getBackgroundId(nightMode: Boolean) =
		if (nightMode) R.drawable.context_menu_card_dark else R.drawable.context_menu_card_light
}
