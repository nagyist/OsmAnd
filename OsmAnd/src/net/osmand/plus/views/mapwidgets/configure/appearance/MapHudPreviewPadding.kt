package net.osmand.plus.views.mapwidgets.configure.appearance

import android.view.View
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity

object MapHudPreviewPadding {

	@JvmStatic
	fun update(mapActivity: MapActivity, topOverlay: View?, bottomOverlay: View?): Boolean {
		val hud = mapActivity.findViewById<View>(R.id.map_hud_layout) ?: return false
		val hudLocation = IntArray(2)
		hud.getLocationOnScreen(hudLocation)
		val hudTop = hudLocation[1]
		val hudBottom = hudTop + hud.height

		val topPadding = topOverlay?.let { overlay ->
			val location = IntArray(2)
			overlay.getLocationOnScreen(location)
			(location[1] + overlay.height - hudTop).coerceAtLeast(0)
		} ?: 0

		val bottomPadding = bottomOverlay?.let { overlay ->
			val location = IntArray(2)
			overlay.getLocationOnScreen(location)
			(hudBottom - location[1]).coerceAtLeast(0)
		} ?: 0

		val changed = hud.paddingTop != topPadding || hud.paddingBottom != bottomPadding
		if (changed) {
			hud.setPadding(hud.paddingLeft, topPadding, hud.paddingRight, bottomPadding)
		}
		return changed
	}

	@JvmStatic
	fun reset(mapActivity: MapActivity) {
		val hud = mapActivity.findViewById<View>(R.id.map_hud_layout) ?: return
		if (hud.paddingTop != 0 || hud.paddingBottom != 0) {
			hud.setPadding(hud.paddingLeft, 0, hud.paddingRight, 0)
		}
	}
}