package net.osmand.plus.mapcontextmenu.builders.rows

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import net.osmand.OnResultCallback
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialSimpleListBottomSheet
import net.osmand.plus.utils.AndroidUtils
import net.osmand.plus.utils.ColorUtilities

class PoiAdditionalActionsBottomSheet : BaseMaterialSimpleListBottomSheet() {

	private var title: String = ""
	private var values: List<String> = emptyList()
	private var resultCallback: OnResultCallback<String>? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		title = arguments?.getString(ARG_TITLE).orEmpty()
		values = arguments?.getStringArrayList(ARG_VALUES).orEmpty()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		super.onCreateView(inflater, container, savedInstanceState)
		val content = mainView.findViewById<LinearLayout>(R.id.bottomSheetContent)
		content.minimumHeight = getDimensionPixelSize(R.dimen.bottom_sheet_menu_peek_height)
		content.setPadding(
			content.paddingLeft,
			content.paddingTop,
			content.paddingRight,
			getDimensionPixelSize(R.dimen.bottom_sheet_content_margin)
		)
		mainView.findViewById<TextView>(R.id.title).text = title
		val itemsContainer = mainView.findViewById<LinearLayout>(R.id.itemsContainer)
		values.forEachIndexed { index, value ->
			itemsContainer.addView(createRow(value, index < values.lastIndex))
		}
		return mainView
	}

	override fun initialBottomSheetState(): Int = BottomSheetBehavior.STATE_EXPANDED

	override fun shouldSkipCollapsed(): Boolean = true

	override fun onBottomSheetReady(
		bottomSheet: FrameLayout,
		behavior: BottomSheetBehavior<FrameLayout>
	) {
		super.onBottomSheetReady(bottomSheet, behavior)
		bottomSheet.doOnPreDraw {
			val contentHeight = mainView.findViewById<View>(R.id.bottomSheetContent).height
			behavior.peekHeight = contentHeight
			behavior.state = BottomSheetBehavior.STATE_EXPANDED
		}
	}

	private fun createRow(value: String, showDivider: Boolean): View {
		return FrameLayout(requireContext()).apply {
			addView(TextView(context).apply {
				text = value
				textSize = 16f
				maxLines = 2
				ellipsize = android.text.TextUtils.TruncateAt.END
				gravity = android.view.Gravity.CENTER_VERTICAL
				setTextColor(ColorUtilities.getActiveColor(osmandApp, nightMode))
				setPadding(dpToPx(16f), dpToPx(8f), dpToPx(16f), dpToPx(8f))
				minHeight = getDimensionPixelSize(R.dimen.bottom_sheet_medium_list_item_height)
				background = ContextCompat.getDrawable(context, AndroidUtils.resolveAttribute(context, android.R.attr.selectableItemBackground))
				setOnClickListener {
					resultCallback?.onResult(value)
					dismiss()
				}
			}, FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			))
			if (showDivider) {
				addView(createDivider(), FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					1,
					android.view.Gravity.BOTTOM
				).apply {
					marginStart = dpToPx(16f)
				})
			}
			setOnClickListener {
				resultCallback?.onResult(value)
				dismiss()
			}
		}
	}

	private fun createDivider(): View {
		return View(requireContext()).apply {
			setBackgroundColor(AndroidUtils.getColorFromAttr(context, R.attr.divider_color))
		}
	}

	companion object {
		private val TAG = PoiAdditionalActionsBottomSheet::class.java.simpleName
		private const val ARG_TITLE = "title"
		private const val ARG_VALUES = "values"

		@JvmStatic
		fun showInstance(
			activity: FragmentActivity,
			title: String?,
			values: ArrayList<String>,
			listener: OnResultCallback<String>
		) {
			val manager = activity.supportFragmentManager
			if (!AndroidUtils.isFragmentCanBeAdded(manager, TAG) || values.isEmpty()) {
				return
			}
			PoiAdditionalActionsBottomSheet().apply {
				resultCallback = listener
				arguments = Bundle().apply {
					putString(ARG_TITLE, title)
					putStringArrayList(ARG_VALUES, values)
				}
				show(manager, TAG)
			}
		}
	}
}
