package net.osmand.plus.views.mapwidgets.configure.appearance

import net.osmand.plus.OsmandApplication
import net.osmand.plus.Version
import net.osmand.plus.inapp.InAppPurchaseUtils

object PanelAppearanceEntitlement {

	@JvmStatic
	fun isCustomBackgroundAvailable(app: OsmandApplication): Boolean {
		return !Version.isFreeVersion(app)
				|| InAppPurchaseUtils.isFullVersionAvailable(app, false)
				|| InAppPurchaseUtils.isMapsPlusAvailable(app, false)
				|| InAppPurchaseUtils.isOsmAndProAvailable(app, false)
				|| InAppPurchaseUtils.isBrandPromoAvailable(app)
				|| Version.isDeveloperBuild(app)
	}
}