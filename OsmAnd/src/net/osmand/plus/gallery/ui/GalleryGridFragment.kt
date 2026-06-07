package net.osmand.plus.gallery.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.GridLayoutManager
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.base.BaseFullScreenFragment
import net.osmand.plus.gallery.contract.IGalleryGridView
import net.osmand.plus.gallery.controller.GalleryGridController
import net.osmand.plus.gallery.model.GalleryItem
import net.osmand.plus.helpers.AndroidUiHelper
import net.osmand.plus.helpers.AndroidUiHelper.isOrientationPortrait
import net.osmand.plus.utils.AndroidUtils
import net.osmand.plus.utils.ColorUtilities
import net.osmand.plus.utils.InsetTarget
import net.osmand.plus.utils.InsetTargetsCollection

class GalleryGridFragment : BaseFullScreenFragment(), IGalleryGridView {

	private lateinit var toolbar: Toolbar
	private lateinit var recyclerView: GalleryGridRecyclerView
	private lateinit var adapter: GalleryGridAdapter
	private lateinit var scaleDetector: ScaleGestureDetector
	private lateinit var layoutManager: GridLayoutManager

	private var controller: GalleryGridController? = null

	@SuppressLint("ClickableViewAccessibility")
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View? {
		updateNightMode()

		val controllerId = arguments?.getString(CONTROLLER_ID_KEY) ?: return null
		controller = app.dialogManager.findController(controllerId) as? GalleryGridController
			?: return null
		controller?.attach(this)

		val view = inflate(R.layout.gallery_grid_fragment, container, false)
		AndroidUtils.addStatusBarPadding21v(requireMyActivity(), view)

		setupScaleDetector()
		setupRecyclerView(view)

		toolbar = view.findViewById(R.id.toolbar)
		setupToolbar()
		setupOnBackPressedCallback()

		return view
	}

	private fun setupRecyclerView(view: View) {
		recyclerView = view.findViewById(R.id.content_list)
		recyclerView.viewTreeObserver.addOnGlobalLayoutListener(
			object : ViewTreeObserver.OnGlobalLayoutListener {
				override fun onGlobalLayout() {
					recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
					val ctrl = controller ?: return
					val activity = mapActivity ?: return

					adapter = ctrl.createAdapter(activity, recyclerView.measuredWidth, nightMode)
					adapter.setItems(ctrl.getGalleryItems())

					recyclerView.adapter = adapter
					recyclerView.setScaleDetector(scaleDetector)

					val spanCount = ctrl.getSpanCount(isPortrait())
					layoutManager = GridLayoutManager(app, spanCount).apply {
						spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
							override fun getSpanSize(position: Int): Int =
								if (adapter.isRegularMediaItemOnPosition(position)) 1
								else this@apply.spanCount
						}
					}
					recyclerView.layoutManager = layoutManager
					recyclerView.addItemDecoration(GalleryGridItemDecorator(app))
				}
			}
		)
	}

	@SuppressLint("ClickableViewAccessibility")
	private fun setupScaleDetector() {
		scaleDetector = ScaleGestureDetector(requireMapActivity(),
			object : ScaleGestureDetector.OnScaleGestureListener {
				override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
					controller?.onScaleBegin()
					return true
				}

				override fun onScale(detector: ScaleGestureDetector): Boolean {
					controller?.onScaleChanged(detector.scaleFactor)
					return true
				}

				override fun onScaleEnd(detector: ScaleGestureDetector) {
					controller?.onScaleEnd()
				}
			}
		)
	}

	override fun updateSpan() {
		if (!::layoutManager.isInitialized || !::adapter.isInitialized) return

		val ctrl = controller ?: return
		val newSpanCount = ctrl.getSpanCount(isPortrait())
		layoutManager.spanCount = newSpanCount
		for (i in 0 until adapter.itemCount) {
			if (adapter.getItem(i) is GalleryItem.Media) {
				adapter.notifyItemChanged(i)
			}
		}
	}

	private fun setupToolbar() {
		toolbar.findViewById<TextView>(R.id.toolbar_title).text = controller?.getScreenTitle()
		toolbar.findViewById<ImageView>(R.id.back_button).apply {
			setOnClickListener { onBackPressed() }
			setImageDrawable(getContentIcon(AndroidUtils.getNavigationIconResId(app)))
		}
		AndroidUiHelper.updateVisibility(toolbar.findViewById(R.id.toolbar_subtitle), false)
	}

	private fun setupOnBackPressedCallback() {
		requireActivity().onBackPressedDispatcher.addCallback(
			viewLifecycleOwner,
			object : OnBackPressedCallback(true) {
				override fun handleOnBackPressed() = onBackPressed()
			}
		)
	}

	private fun onBackPressed() {
		activity?.supportFragmentManager?.popBackStack()
	}

	override fun getStatusBarColorId(): Int {
		AndroidUiHelper.setStatusBarContentColor(view, nightMode)
		return ColorUtilities.getListBgColorId(nightMode)
	}

	override fun getContentStatusBarNightMode() = nightMode

	override fun onResume() {
		super.onResume()
		callMapActivity(MapActivity::disableDrawer)
	}

	override fun onPause() {
		super.onPause()
		callMapActivity(MapActivity::enableDrawer)
	}

	override fun onDestroy() {
		super.onDestroy()
		controller?.onScreenDestroyed(activity)
	}

	override fun getInsetTargets(): InsetTargetsCollection {
		return super.getInsetTargets().apply {
			replace(InsetTarget.createScrollable(R.id.content_list))
		}
	}

	// IGalleryGridView
	override fun getMapActivity(): MapActivity? = super.getMapActivity()
	override fun isNightMode(): Boolean = nightMode
	override fun isPortrait(): Boolean = isOrientationPortrait(requireActivity())

	companion object {
		const val TAG = "GalleryGridFragment"
		private const val CONTROLLER_ID_KEY = "controller_id"

		@JvmStatic
		fun showInstance(activity: FragmentActivity, controllerId: String) {
			val manager: FragmentManager = activity.supportFragmentManager
			if (AndroidUtils.isFragmentCanBeAdded(manager, TAG)) {
				manager.beginTransaction()
					.add(R.id.fragmentContainer, newInstance(controllerId), TAG)
					.addToBackStack(TAG)
					.commitAllowingStateLoss()
			}
		}

		private fun newInstance(controllerId: String) = GalleryGridFragment().apply {
			arguments = Bundle().apply { putString(CONTROLLER_ID_KEY, controllerId) }
		}
	}
}