package net.osmand.plus.track

class Track3DStyle(var visualizationType: Gpx3DVisualizationType,
	var wallColorType: Gpx3DWallColorType,
	var linePositionType: Gpx3DLinePositionType,
	var additionalExaggeration: Int) {

	override fun equals(other: Any?): Boolean {
		return super.equals(other) && other is Track3DStyle
				&& visualizationType == other.visualizationType
				&& wallColorType == other.wallColorType
				&& linePositionType == other.linePositionType
				&& additionalExaggeration == other.additionalExaggeration
	}
}