package net.osmand.plus.mapcontextmenu.builders.rows

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialSimpleListBottomSheet
import net.osmand.plus.utils.AndroidUtils

class PoiAdditionalActionsBottomSheet : BaseMaterialSimpleListBottomSheet() {

	private var title: String = ""
	private var values: List<String> = emptyList()
	private var itemClickListener: OnItemClickListener? = null

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
			itemsContainer.addView(createRow(itemsContainer, value, index < values.lastIndex))
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

	private fun createRow(parent: ViewGroup, value: String, showDivider: Boolean): View {
		return layoutInflater.inflate(R.layout.bottom_sheet_item_active_color_text, parent, false).apply {
			findViewById<TextView>(R.id.itemText).text = value
			findViewById<View>(R.id.divider).visibility = if (showDivider) View.VISIBLE else View.GONE
			setOnClickListener {
				activity?.let { itemClickListener?.onItemClick(it, value) }
				dismiss()
			}
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
			listener: OnItemClickListener
		) {
			val manager = activity.supportFragmentManager
			if (!AndroidUtils.isFragmentCanBeAdded(manager, TAG) || values.isEmpty()) {
				return
			}
			PoiAdditionalActionsBottomSheet().apply {
				itemClickListener = listener
				arguments = Bundle().apply {
					putString(ARG_TITLE, title)
					putStringArrayList(ARG_VALUES, values)
				}
				show(manager, TAG)
			}
		}
	}

	fun interface OnItemClickListener {
		fun onItemClick(activity: FragmentActivity, value: String)
	}
}
