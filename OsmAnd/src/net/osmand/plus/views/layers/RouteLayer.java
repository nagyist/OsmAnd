package net.osmand.plus.views.layers;

import static net.osmand.util.MapUtils.HIGH_LATLON_PRECISION;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint.Cap;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;

import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.core.android.MapRendererView;
import net.osmand.core.jni.MapMarkerBuilder;
import net.osmand.core.jni.MapMarkersCollection;
import net.osmand.core.jni.PointI;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.QuadRect;
import net.osmand.data.RotatedTileBox;
import net.osmand.data.TransportStop;
import net.osmand.plus.ChartPointsHelper;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.base.MapViewTrackingUtilities;
import net.osmand.plus.charts.TrackChartPoints;
import net.osmand.plus.profiles.LocationIcon;
import net.osmand.plus.routing.ColoringTypeAvailabilityCache;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.plus.routing.RouteDirectionInfo;
import net.osmand.plus.routing.RouteService;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.routing.RoutingHelperUtils;
import net.osmand.plus.routing.TransportRoutingHelper;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.NativeUtilities;
import net.osmand.plus.views.layers.ContextMenuLayer.IContextMenuProvider;
import net.osmand.plus.views.layers.base.BaseRouteLayer;
import net.osmand.plus.views.layers.core.LocationPointsTileProvider;
import net.osmand.plus.views.layers.geometry.PublicTransportGeometryWay;
import net.osmand.plus.views.layers.geometry.PublicTransportGeometryWayContext;
import net.osmand.plus.views.layers.geometry.RouteGeometryWay;
import net.osmand.plus.views.layers.geometry.RouteGeometryWayContext;
import net.osmand.router.TransportRouteResult;
import net.osmand.shared.routing.ColoringType;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RouteLayer extends BaseRouteLayer implements IContextMenuProvider {

	private static final Log LOG = PlatformUtil.getLog(RouteLayer.class);

	private final RoutingHelper helper;
	private final TransportRoutingHelper transportHelper;
	private Location lastRouteProjection;

	private final ChartPointsHelper chartPointsHelper;
	private TrackChartPoints trackChartPoints;

	private RenderingLineAttributes attrsPT;
	private RenderingLineAttributes attrsWPT;
	private RenderingLineAttributes attrsW;

	private RouteGeometryWayContext routeWayContext;
	private PublicTransportGeometryWayContext publicTransportWayContext;
	private RouteGeometryWay routeGeometry;
	private PublicTransportGeometryWay publicTransportRouteGeometry;

	private final ColoringTypeAvailabilityCache coloringAvailabilityCache;

	private LayerDrawable projectionIcon;

	private boolean isCreated;

	//OpenGL
	private final RenderState renderState = new RenderState();
	private LocationPointsTileProvider trackChartPointsProvider;
	private MapMarkersCollection highlightedPointCollection;
	private net.osmand.core.jni.MapMarker highlightedPointMarker;
	private LatLon highlightedPointLocationCached;
	private List<LatLon> xAxisPointsCached = new ArrayList<>();
	private MapMarkersCollection projectionPointCollection;
	private net.osmand.core.jni.MapMarker projectedPointMarker;

	private interface ConditionMatcher {
		boolean match();
	}

	public RouteLayer(@NonNull Context context) {
		super(context);
		OsmandApplication app = (OsmandApplication) context.getApplicationContext();
		this.helper = app.getRoutingHelper();
		this.transportHelper = helper.getTransportRoutingHelper();
		this.chartPointsHelper = new ChartPointsHelper(app);
		this.coloringAvailabilityCache = new ColoringTypeAvailabilityCache(app);
	}

	public RoutingHelper getHelper() {
		return helper;
	}

	public void setTrackChartPoints(TrackChartPoints trackChartPoints) {
		this.trackChartPoints = trackChartPoints;
	}

	@Override
	protected void initAttrs(float density) {
		super.initAttrs(density);

		attrsPT = new RenderingLineAttributes("publicTransportLine");
		attrsPT.defaultWidth = (int) (12 * density);
		attrsPT.defaultWidth3 = (int) (7 * density);
		attrsPT.defaultColor = ContextCompat.getColor(getContext(), R.color.nav_track);
		attrsPT.paint3.setStrokeCap(Cap.BUTT);
		attrsPT.paint3.setColor(Color.WHITE);
		attrsPT.paint2.setStrokeCap(Cap.BUTT);
		attrsPT.paint2.setColor(Color.BLACK);

		attrsWPT = new RenderingLineAttributes("walkingRouteLine");
		attrsWPT.defaultWidth = (int) (12 * density);
		attrsWPT.defaultWidth3 = (int) (7 * density);
		attrsWPT.defaultColor = ContextCompat.getColor(getContext(), R.color.nav_track_walk_fill);
		attrsWPT.paint3.setStrokeCap(Cap.BUTT);
		attrsWPT.paint3.setColor(Color.WHITE);
		attrsWPT.paint2.setStrokeCap(Cap.BUTT);
		attrsWPT.paint2.setColor(Color.BLACK);

		attrsW = new RenderingLineAttributes("straightWalkingRouteLine");
		attrsW.defaultWidth = (int) (12 * density);
		attrsW.defaultWidth3 = (int) (7 * density);
		attrsW.defaultColor = ContextCompat.getColor(getContext(), R.color.nav_track_walk_fill);
		attrsW.paint3.setStrokeCap(Cap.BUTT);
		attrsW.paint3.setColor(Color.WHITE);
		attrsW.paint2.setStrokeCap(Cap.BUTT);
		attrsW.paint2.setColor(Color.BLACK);
	}

	@Override
	protected void initGeometries(float density) {
		routeWayContext = new RouteGeometryWayContext(getContext(), density);
		routeWayContext.updatePaints(nightMode, attrs, attrsW);
		routeGeometry = new RouteGeometryWay(routeWayContext);
		routeGeometry.baseOrder = getBaseOrder();

		publicTransportWayContext = new PublicTransportGeometryWayContext(getContext(), density);
		publicTransportWayContext.updatePaints(nightMode, attrs, attrsPT, attrsW);
		publicTransportRouteGeometry = new PublicTransportGeometryWay(publicTransportWayContext);
		publicTransportRouteGeometry.baseOrder = getBaseOrder();
	}

	@Override
	public boolean areMapRendererViewEventsAllowed() {
		return true;
	}

	@Override
	public void onUpdateFrame(MapRendererView mapRenderer) {
		super.onUpdateFrame(mapRenderer);
		ConditionMatcher drawLocationsMatcher = () -> hasMapRenderer() && !helper.isPublicTransportMode()
				&& helper.getFinalLocation() != null && helper.getRoute().isCalculated();
		if (drawLocationsMatcher.match()) {
			getApplication().runInUIThread(() -> {
				if (drawLocationsMatcher.match()) {
					drawLocations(null, view.getRotatedTileBox());
				}
			});
		}
	}

	@Override
	public void onPrepareBufferImage(Canvas canvas, RotatedTileBox tileBox, DrawSettings settings) {
		super.onPrepareBufferImage(canvas, tileBox, settings);
		MapRendererView mapRenderer = getMapRenderer();
		if ((helper.isPublicTransportMode() && transportHelper.getRoutes() != null) ||
				(helper.getFinalLocation() != null && helper.getRoute().isCalculated())) {
			isCreated = true;
			updateRouteColoringType();
			updateAttrs(settings, tileBox);
			updateRouteColors(nightMode);

			drawLocations(canvas, tileBox);

			if (mapRenderer == null) {
				drawXAxisPoints(trackChartPoints, canvas, tileBox);
			} else {
				if (highlightedPointCollection == null || mapActivityInvalidated || mapRendererChanged) {
					recreateHighlightedPointCollection();
				}
				drawXAxisPointsOpenGl(trackChartPoints, mapRenderer, tileBox);
			}
		} else if (isCreated) {
			isCreated = false;
			resetLayer();
		}
		mapActivityInvalidated = false;
		if (mapRenderer != null) {
			mapRendererChanged = false;
		}
	}

	private void drawLocations(@Nullable Canvas canvas, @NonNull RotatedTileBox tileBox) {
		int w = tileBox.getPixWidth();
		int h = tileBox.getPixHeight();
		Location lastProjection = helper.getLastProjection();
		RotatedTileBox cp;
		if (lastProjection != null &&
				tileBox.containsLatLon(lastProjection.getLatitude(), lastProjection.getLongitude())) {
			cp = tileBox.copy();
			cp.increasePixelDimensions(w / 2, h);
		} else {
			cp = tileBox;
		}
		QuadRect latlonRect = cp.getLatLonBounds();
		QuadRect correctedQuadRect = getCorrectedQuadRect(latlonRect);
		drawLocations(tileBox, canvas, correctedQuadRect.top, correctedQuadRect.left,
				correctedQuadRect.bottom, correctedQuadRect.right);
	}

	@Nullable
	public Location getLastRouteProjection() {
		return lastRouteProjection;
	}

	private boolean useMapCenter() {
		OsmandSettings settings = getApplication().getSettings();
		MapViewTrackingUtilities mapViewTrackingUtilities = getApplication().getMapViewTrackingUtilities();
		return settings.ANIMATE_MY_LOCATION.get()
				&& !mapViewTrackingUtilities.isMovingToMyLocation()
				&& mapViewTrackingUtilities.isMapLinkedToLocation();
	}

	private void drawXAxisPoints(@Nullable TrackChartPoints chartPoints, @NonNull Canvas canvas,
	                             @NonNull RotatedTileBox tileBox) {
		if (chartPoints != null) {
			canvas.rotate(-tileBox.getRotate(), tileBox.getCenterPixelX(), tileBox.getCenterPixelY());
			List<LatLon> xAxisPoints = chartPoints.getXAxisPoints();
			if (!Algorithms.isEmpty(xAxisPoints)) {
				chartPointsHelper.drawXAxisPoints(xAxisPoints, attrs.defaultColor, canvas, tileBox);
			}
			LatLon highlightedPoint = chartPoints.getHighlightedPoint();
			if (highlightedPoint != null) {
				chartPointsHelper.drawHighlightedPoint(highlightedPoint, canvas, tileBox);
			}
			canvas.rotate(tileBox.getRotate(), tileBox.getCenterPixelX(), tileBox.getCenterPixelY());
		}
	}

	private void drawXAxisPointsOpenGl(@Nullable TrackChartPoints chartPoints, @NonNull MapRendererView mapRenderer,
	                                   @NonNull RotatedTileBox tileBox) {
		if (chartPoints != null) {
			if (trackChartPoints != null && trackChartPoints.getHighlightedPoint() != null) {
				LatLon highlightedPoint = trackChartPoints.getHighlightedPoint();
				if (!Algorithms.objectEquals(highlightedPointLocationCached, highlightedPoint)) {
					highlightedPointLocationCached = highlightedPoint;
					setHighlightedPointMarkerLocation(highlightedPoint);
					setHighlightedPointMarkerVisibility(true);
				}
			} else {
				setHighlightedPointMarkerVisibility(false);
			}

			List<LatLon> xAxisPoints = chartPoints.getXAxisPoints();
			if (Algorithms.objectEquals(xAxisPointsCached, xAxisPoints) && trackChartPointsProvider != null
					&& !mapActivityInvalidated && !mapRendererChanged) {
				return;
			}
			xAxisPointsCached = xAxisPoints;
			clearXAxisPoints();
			if (!Algorithms.isEmpty(xAxisPoints)) {
				Bitmap pointBitmap = chartPointsHelper.createXAxisPointBitmap(attrs.defaultColor, tileBox.getDensity());
				trackChartPointsProvider = new LocationPointsTileProvider(getPointsOrder() - 2000, xAxisPoints, pointBitmap);
				trackChartPointsProvider.drawPoints(mapRenderer);
			}
		} else {
			xAxisPointsCached = new ArrayList<>();
			highlightedPointLocationCached = null;
			setHighlightedPointMarkerVisibility(false);
			clearXAxisPoints();
		}
	}

	private void setHighlightedPointMarkerLocation(@NonNull LatLon latLon) {
		MapRendererView mapRenderer = getMapRenderer();
		if (mapRenderer != null && highlightedPointMarker != null) {
			highlightedPointMarker.setPosition(new PointI(MapUtils.get31TileNumberX(latLon.getLongitude()),
					MapUtils.get31TileNumberY(latLon.getLatitude())));
		}
	}

	private void setHighlightedPointMarkerVisibility(boolean visible) {
		if (highlightedPointMarker != null) {
			highlightedPointMarker.setIsHidden(!visible);
		}
	}

	private void clearXAxisPoints() {
		MapRendererView mapRenderer = getMapRenderer();
		if (mapRenderer != null && trackChartPointsProvider != null) {
			trackChartPointsProvider.clearPoints(mapRenderer);
			trackChartPointsProvider = null;
		}
	}

	private void recreateHighlightedPointCollection() {
		MapRendererView mapRenderer = getMapRenderer();
		if (mapRenderer != null) {
			if (highlightedPointCollection != null) {
				mapRenderer.removeSymbolsProvider(highlightedPointCollection);
			}
			highlightedPointCollection = new MapMarkersCollection();
			MapMarkerBuilder builder = new MapMarkerBuilder();
			builder.setBaseOrder(getPointsOrder() - 2100);
			builder.setIsAccuracyCircleSupported(false);
			builder.setIsHidden(true);
			builder.setPinIcon(NativeUtilities.createSkImageFromBitmap(
					chartPointsHelper.createHighlightedPointBitmap()));
			highlightedPointMarker = builder.buildAndAddToCollection(highlightedPointCollection);
			mapRenderer.addSymbolsProvider(highlightedPointCollection);
		}
	}

	private void setProjectedPointMarkerLocation(double lat, double lon) {
		MapRendererView mapRenderer = getMapRenderer();
		if (mapRenderer != null && projectedPointMarker != null) {
			projectedPointMarker.setPosition(new PointI(MapUtils.get31TileNumberX(lon),
					MapUtils.get31TileNumberY(lat)));
		}
	}

	private void setProjectedPointMarkerVisibility(boolean visible) {
		if (projectedPointMarker != null) {
			projectedPointMarker.setIsHidden(!visible);
		}
	}

	private void recreateProjectedPointCollection() {
		MapRendererView mapRenderer = getMapRenderer();
		if (mapRenderer != null) {
			if (projectionPointCollection != null) {
				mapRenderer.removeSymbolsProvider(projectionPointCollection);
			}
			projectionPointCollection = new MapMarkersCollection();
			MapMarkerBuilder builder = new MapMarkerBuilder();
			builder.setBaseOrder(getPointsOrder() - 2110);
			builder.setIsAccuracyCircleSupported(false);
			builder.setIsHidden(true);
			builder.setPinIcon(NativeUtilities.createSkImageFromBitmap(
					chartPointsHelper.createHighlightedPointBitmap()));
			projectedPointMarker = builder.buildAndAddToCollection(projectionPointCollection);
			mapRenderer.addSymbolsProvider(projectionPointCollection);
		}
	}

	private void removeProjectedPointCollection() {
		MapRendererView mapRenderer = getMapRenderer();
		if (mapRenderer != null && projectionPointCollection != null) {
			mapRenderer.removeSymbolsProvider(projectionPointCollection);
			projectedPointMarker = null;
		}
	}

	@Override
	protected void updateAttrs(DrawSettings settings, RotatedTileBox tileBox) {
		boolean updatePaints = attrs.updatePaints(view.getApplication(), settings, tileBox);
		attrs.isPaint3 = false;
		attrs.isPaint2 = false;
		attrsPT.updatePaints(view.getApplication(), settings, tileBox);
		attrsPT.isPaint3 = false;
		attrsPT.isPaint2 = false;
		attrsWPT.updatePaints(view.getApplication(), settings, tileBox);
		attrsPT.isPaint3 = false;
		attrsPT.isPaint2 = false;
		attrsW.updatePaints(view.getApplication(), settings, tileBox);
		float routeLineWidth = getRouteLineWidth(tileBox);
		updatePaints |= attrsW.paint.getStrokeWidth() != routeLineWidth;
		attrsW.paint.setStrokeWidth(routeLineWidth);

		nightMode = settings != null && settings.isNightMode();

		if (updatePaints) {
			routeWayContext.updatePaints(nightMode, attrs, attrsW);
			publicTransportWayContext.updatePaints(nightMode, attrs, attrsPT, attrsWPT);
		}
	}

	@Override
	protected void updateTurnArrowColor() {
		if (routeColoringType.isGradient() && isColoringAvailable(routeColoringType, null)) {
			customTurnArrowColor = Color.WHITE;
		} else {
			customTurnArrowColor = attrs.paint3.getColor();
		}
		paintIconAction.setColorFilter(new PorterDuffColorFilter(customTurnArrowColor, PorterDuff.Mode.MULTIPLY));
	}

	private void drawActionArrows(@NonNull RotatedTileBox tb, @NonNull Canvas canvas, @NonNull List<ActionPoint> actionPoints) {
		if (actionPoints.size() > 0) {
			canvas.rotate(-tb.getRotate(), tb.getCenterPixelX(), tb.getCenterPixelY());
			try {
				float routeWidth = routeGeometry.getDefaultWayStyle().getWidth(0);
				Path path = new Path();
				Matrix matrix = new Matrix();
				float x = 0, px = 0, py = 0, y = 0;
				List<List<ActionPoint>> actionArrows = routeGeometry.getActionArrows(actionPoints);

				for (List<ActionPoint> arrow : actionArrows) {
					int arrowColor = routeGeometry.getContrastArrowColor(arrow, customTurnArrowColor);

					for (int i = 0; i < arrow.size(); i++) {
						ActionPoint actionPoint = arrow.get(i);
						double lat = actionPoint.location.getLatitude();
						double lon = actionPoint.location.getLongitude();

						px = x;
						py = y;
						x = tb.getPixXFromLatLon(lat, lon);
						y = tb.getPixYFromLatLon(lat, lon);

						if (i == 0) {
							path.reset();
							path.moveTo(x, y);
						} else {
							path.lineTo(x, y);
						}
					}

					int styleTurnArrowColor = attrs.paint3.getColor();
					setTurnArrowPaintsColor(arrowColor);
					if (routeWidth != 0) {
						attrs.paint3.setStrokeWidth(routeWidth / 2);
					}
					canvas.drawPath(path, attrs.paint3);
					drawTurnArrow(canvas, matrix, x, y, px, py);
					setTurnArrowPaintsColor(styleTurnArrowColor);
				}
			} finally {
				canvas.rotate(tb.getRotate(), tb.getCenterPixelX(), tb.getCenterPixelY());
			}
		}
	}

	private void drawProjectionPoint(@NonNull Canvas canvas, double[] projectionXY) {
		if (projectionIcon == null) {
			helper.getSettings().getApplicationMode().getLocationIcon();
			projectionIcon = (LayerDrawable) AppCompatResources.getDrawable(getContext(), LocationIcon.STATIC_DEFAULT.getIconId());
		}
		if (projectionIcon != null) {
			int locationX = (int) projectionXY[0];
			int locationY = (int) projectionXY[1];
			drawIcon(canvas, projectionIcon, locationX, locationY);
		}
	}

	public void drawLocations(@NonNull RotatedTileBox tb, @Nullable Canvas canvas,
	                          double topLatitude, double leftLongitude, double bottomLatitude, double rightLongitude) {
		if (helper.isPublicTransportMode()) {
			publicTransportRouteGeometry.baseOrder = getBaseOrder();
			int currentRoute = transportHelper.getCurrentRoute();
			if (publicTransportRouteGeometry.hasMapRenderer()) {
				renderState.updateTransportRouteState(currentRoute);
				if (renderState.shouldRebuildTransportRoute) {
					publicTransportRouteGeometry.resetSymbolProviders();
				}
			}
			List<TransportRouteResult> routes = transportHelper.getRoutes();
			TransportRouteResult route = routes != null && routes.size() > currentRoute ? routes.get(currentRoute) : null;
			routeGeometry.clearRoute();
			boolean routeUpdated = publicTransportRouteGeometry.updateRoute(tb, route);
			boolean draw = routeUpdated || renderState.shouldRebuildTransportRoute
					|| !publicTransportRouteGeometry.hasMapRenderer() || mapActivityInvalidated || mapRendererChanged;
			if (route != null && draw) {
				LatLon start = transportHelper.getStartLocation();
				Location startLocation = new Location("transport");
				startLocation.setLatitude(start.getLatitude());
				startLocation.setLongitude(start.getLongitude());
				publicTransportRouteGeometry.drawSegments(tb, canvas, topLatitude, leftLongitude, bottomLatitude, rightLongitude,
						startLocation, 0);
			}
		} else {
			routeGeometry.baseOrder = getBaseOrder();
			RouteCalculationResult route = helper.getRoute();
			boolean directTo = route.getRouteService() == RouteService.DIRECT_TO;
			boolean straight = route.getRouteService() == RouteService.STRAIGHT;
			publicTransportRouteGeometry.clearRoute();

			ColoringType actualColoringType = isColoringAvailable(routeColoringType, routeInfoAttribute) ?
							routeColoringType : ColoringType.DEFAULT;
			int routeLineColor = getRouteLineColor();
			float routeLineWidth = getRouteLineWidth(tb);
			boolean shouldShowDirectionArrows = shouldShowDirectionArrows();
			routeGeometry.setRouteStyleParams(routeLineColor, routeLineWidth, shouldShowDirectionArrows,
					getDirectionArrowsColor(), actualColoringType, routeInfoAttribute, routeGradientPalette);
			boolean routeUpdated = routeGeometry.updateRoute(tb, route);
			boolean shouldShowTurnArrows = shouldShowTurnArrows();

			Location lastProjection;
			int startLocationIndex;
			if (directTo) {
				lastProjection = null;
				startLocationIndex = 0;
			} else if (route.getCurrentStraightAngleRoute() > 0) {
				Location lastFixedLocation = helper.getLastFixedLocation();
				Location currentLocation = new Location(lastFixedLocation);
				MapRendererView mapRenderer = getMapRenderer();
				OsmandApplication app = getApplication();
				boolean useMapCenter = useMapCenter();
				if (mapRenderer != null) {
					if (useMapCenter) {
						PointI target31 = mapRenderer.getTarget();
						currentLocation.setLatitude(MapUtils.get31LatitudeY(target31.getY()));
						currentLocation.setLongitude(MapUtils.get31LongitudeX(target31.getX()));
					} else {
						LatLon lastMarkerLocation = app.getOsmandMap().getMapLayers().getLocationLayer().getLastMarkerLocation();
						if (lastMarkerLocation != null) {
							currentLocation.setLatitude(lastMarkerLocation.getLatitude());
							currentLocation.setLongitude(lastMarkerLocation.getLongitude());
						}
					}
				} else if (useMapCenter) {
					currentLocation.setLatitude(tb.getLatitude());
					currentLocation.setLongitude(tb.getLongitude());
				}
				List<Location> locations = route.getImmutableAllLocations();
				int currentRoute = route.getCurrentRouteForLocation(currentLocation);
				if (currentRoute > 0) {
					Location previousRouteLocation = locations.get(currentRoute - 1);
					Location currentRouteLocation = locations.get(currentRoute);
					lastProjection = RoutingHelperUtils.getProject(currentLocation, previousRouteLocation, currentRouteLocation);
					float calcbearing = !MapUtils.areLatLonEqual(previousRouteLocation, currentRouteLocation) ? previousRouteLocation.bearingTo(currentRouteLocation) :
							previousRouteLocation.bearingTo(currentLocation);
					lastProjection.setBearing(MapUtils.normalizeDegrees360(calcbearing));
					if (currentLocation.distanceTo(lastProjection) > helper.getMaxAllowedProjectDist(currentLocation)) {
						lastProjection = null;
					} else if (app.getSettings().SNAP_TO_ROAD.get() && currentRoute + 1 < locations.size()) {
						// Not needed here as this code for preview turns
//						Location nextRouteLocation = locations.get(currentRoute + 1);
//						RoutingHelperUtils.approximateBearingIfNeeded(helper, lastProjection, currentLocation,
//								previousRouteLocation, currentRouteLocation, nextRouteLocation, true);
					}
				} else {
					lastProjection = null;
				}
				startLocationIndex = currentRoute;
			} else {
				lastProjection = straight || routeUpdated ? helper.getLastFixedLocation() : helper.getLastProjection();
				startLocationIndex = route.getCurrentStraightAngleRoute();
			}
			lastRouteProjection = lastProjection;
			boolean draw = true;
			if (routeGeometry.hasMapRenderer()) {
				renderState.updateRouteState(lastProjection, startLocationIndex, actualColoringType, routeLineColor,
						routeLineWidth, route.getCurrentRoute(), tb.getZoom(), shouldShowTurnArrows, shouldShowDirectionArrows);
				draw = routeUpdated || renderState.shouldRebuildRoute || mapActivityInvalidated || mapRendererChanged;
				if (draw) {
					routeGeometry.resetSymbolProviders();
				} else {
					draw = renderState.shouldUpdateRoute;
				}
			}

			List<ActionPoint> actionPoints = null;
			boolean drawTurnArrows = !directTo && tb.getZoom() >= 14 && shouldShowTurnArrows;
			if (drawTurnArrows) {
				if (routeGeometry.hasMapRenderer()) {
					if (routeUpdated || renderState.shouldUpdateActionPoints || mapActivityInvalidated || mapRendererChanged) {
						actionPoints = calculateActionPoints(helper.getLastProjection(),
								route.getRouteLocations(), route.getCurrentRoute(), tb.getZoom());
					}
				} else if (canvas != null) {
					actionPoints = calculateActionPoints(topLatitude, leftLongitude,
							bottomLatitude, rightLongitude, helper.getLastProjection(),
							route.getRouteLocations(), route.getCurrentRoute(), tb.getZoom());
				}
			}

			if (draw) {
				routeGeometry.setForceIncludedPointIndexesFromActionPoints(actionPoints);
				routeGeometry.drawSegments(tb, canvas, topLatitude, leftLongitude, bottomLatitude, rightLongitude,
						lastProjection, startLocationIndex);
			}

			if (actionPoints != null) {
				if (routeGeometry.hasMapRenderer()) {
					routeGeometry.buildActionArrows(actionPoints, customTurnArrowColor);
				} else if (canvas != null) {
					drawActionArrows(tb, canvas, actionPoints);
				}
			} else if (!drawTurnArrows) {
				if (routeGeometry.hasMapRenderer()) {
					routeGeometry.resetActionLines();
				}
			}
			if (directTo) {
				MapRendererView mapRenderer = getMapRenderer();
				if (mapRenderer != null) {
					if (projectionPointCollection == null || mapActivityInvalidated || mapRendererChanged) {
						recreateProjectedPointCollection();
					}
					double[] projectionOnRoute = calculateProjectionOnRoutePoint(helper, tb, false);
					//double[] projectionOnRoute = calculateProjectionOnRoutePoint(helper);
					if (projectionOnRoute != null) {
						setProjectedPointMarkerLocation(projectionOnRoute[0], projectionOnRoute[1]);
					}
					setProjectedPointMarkerVisibility(projectionOnRoute != null);
				} else {
					//add projection point on original route
					double[] projectionOnRoute = calculateProjectionOnRoutePoint(helper, tb, true);
					if (projectionOnRoute != null && canvas != null) {
						drawProjectionPoint(canvas, projectionOnRoute);
					}
				}
			} else {
				removeProjectedPointCollection();
			}
		}
	}

	@Nullable
	private double[] calculateProjectionOnRoutePoint(RoutingHelper helper, RotatedTileBox box, boolean outPixel) {
		double[] projectionXY = null;
		Location ll = helper.getLastFixedLocation();
		RouteCalculationResult route = helper.getRoute();
		List<Location> locs = route.getImmutableAllLocations();
		int cr = route.getCurrentRoute();
		int locIndex = locs.size() - 1;
		if (route.getIntermediatePointsToPass() > 0) {
			locIndex = route.getIndexOfIntermediate(route.getIntermediatePointsToPass() - 1);
		}
		if (ll != null && cr > 0 && cr < locs.size() && locIndex >= 0 && locIndex < locs.size()) {
			Location loc1 = locs.get(cr - 1);
			Location loc2 = locs.get(cr);
			double distLeft = route.getDistanceFromPoint(cr) - route.getDistanceFromPoint(locIndex);
			double baDist = route.getDistanceFromPoint(cr - 1) - route.getDistanceFromPoint(cr);
			Location target = locs.get(locIndex);
			double dTarget = ll.distanceTo(target);
			if (outPixel) {
				int aX = box.getPixXFromLonNoRot(loc1.getLongitude());
				int aY = box.getPixYFromLatNoRot(loc1.getLatitude());
				int bX = box.getPixXFromLonNoRot(loc2.getLongitude());
				int bY = box.getPixYFromLatNoRot(loc2.getLatitude());
				if (baDist != 0) {
					double CF = (dTarget - distLeft) / baDist;
					double rX = bX - CF * (bX - aX);
					double rY = bY - CF * (bY - aY);
					projectionXY = new double[]{rX, rY};
				}
			} else {
				double l1Lon = loc1.getLongitude();
				double l1Lat = loc1.getLatitude();
				double l2Lon = loc2.getLongitude();
				double l2Lat = loc2.getLatitude();
				if (baDist != 0) {
					double CF = (dTarget - distLeft) / baDist;
					double lon = l2Lon - CF * (l2Lon - l1Lon);
					double lat = l2Lat - CF * (l2Lat - l1Lat);
					return new double[]{l2Lat, l2Lon};
				} else {
					return null;
				}
			}
		}
		if (projectionXY != null) {

			double distanceLoc2Proj = MapUtils.getSqrtDistance((int)projectionXY[0], (int) projectionXY[1],
					box.getPixXFromLonNoRot(ll.getLongitude()), box.getPixYFromLatNoRot(ll.getLatitude()));
			boolean visible = box.containsPoint((float) projectionXY[0], (float) projectionXY[1], 20.0f)
					&& distanceLoc2Proj > AndroidUtils.dpToPx(getContext(), 52) / 2.0;
			if (visible) {
				return projectionXY;
			}
		}
		return null;
	}

	private List<ActionPoint> calculateActionPoints(Location lastProjection, List<Location> routeNodes, int cd, int zoom) {
		return calculateActionPoints(0, 0, 0, 0, lastProjection, routeNodes, cd, zoom);
	}

	private List<ActionPoint> calculateActionPoints(double topLatitude, double leftLongitude, double bottomLatitude,
			double rightLongitude, Location lastProjection, List<Location> routeNodes, int cd, int zoom) {
		Iterator<RouteDirectionInfo> it = helper.getRouteDirections().iterator();
		RouteDirectionInfo nf = null;

		int currentRoute = helper.getRoute().getCurrentRoute();
		
		double DISTANCE_ACTION = 35;
		if(zoom >= 17) {
			DISTANCE_ACTION = 15;
		} else if (zoom == 15) {
			DISTANCE_ACTION = 70;
		} else if (zoom < 15) {
			DISTANCE_ACTION = 110;
		}
		double actionDist = 0;
		Location previousAction = null;
		List<ActionPoint> actionPoints = new ArrayList<>();
		int prevFinishPoint = -1;
		for (int routePoint = 0; routePoint < routeNodes.size(); routePoint++) {
			Location loc = routeNodes.get(routePoint);
			if (RouteCalculationResult.FIRST_LAST_LOCATION_PROVIDER.equals(loc.getProvider())) {
				continue;
			}
			if (nf != null) {
				int pnt = nf.routeEndPointOffset == 0 ? nf.routePointOffset : nf.routeEndPointOffset;
				if(pnt < routePoint + cd ) {
					nf = null;
				}
			}
			while (nf == null && it.hasNext()) {
				nf = it.next();
				int pnt = nf.routeEndPointOffset == 0 ? nf.routePointOffset : nf.routeEndPointOffset;
				if (pnt < routePoint + cd) {
					nf = null;
				}
			}
			boolean action = nf != null && (nf.routePointOffset == routePoint + cd ||
					(nf.routePointOffset <= routePoint + cd && routePoint + cd  <= nf.routeEndPointOffset));
			if (!action && previousAction == null) {
				// no need to check
				continue;
			}
			boolean visible = (leftLongitude == rightLongitude) ||
					(leftLongitude <= loc.getLongitude()
							&& loc.getLongitude() <= rightLongitude
							&& bottomLatitude <= loc.getLatitude()
							&& loc.getLatitude() <= topLatitude);
			if (action && !visible && previousAction == null) {
				continue;
			}
			if (!action) {
				// previousAction != null
				float dist = loc.distanceTo(previousAction);
				actionDist += dist;
				if (actionDist >= DISTANCE_ACTION) {
					double normalizedOffset = 1 - (actionDist - DISTANCE_ACTION) / dist;
					Location projection = calculateProjection(normalizedOffset, previousAction, loc);
					actionPoints.add(new ActionPoint(projection, routePoint - 1 + currentRoute, normalizedOffset));
					actionPoints.add(null);
					prevFinishPoint = routePoint;
					previousAction = null;
					actionDist = 0;
				} else {
					actionPoints.add(new ActionPoint(loc, routePoint + currentRoute, 0.0f));
					previousAction = loc;
				}
			} else {
				// action point
				if (previousAction == null && lastProjection != null) {
					addPreviousToActionPoints(actionPoints, lastProjection, routeNodes, DISTANCE_ACTION,
							prevFinishPoint, routePoint, loc);
				}
				actionPoints.add(new ActionPoint(loc, routePoint + currentRoute, 0.0f));
				previousAction = loc;
				prevFinishPoint = -1;
				actionDist = 0;
			}
		}
		if (previousAction != null) {
			actionPoints.add(null);
		}
		return actionPoints;
	}


	private void addPreviousToActionPoints(List<ActionPoint> actionPoints, Location lastProjection,
	                                       List<Location> routeNodes, double distanceAction,
	                                       int prevFinishPoint, int routePoint, Location loc) {
		int currentRoute = helper.getRoute().getCurrentRoute();

		// put some points in front
		int ind = actionPoints.size();
		Location lprevious = loc;
		double dist = 0;
		for (int k = routePoint - 1; k >= -1; k--) {
			Location location = k == -1 ? lastProjection : routeNodes.get(k);
			int actionPointIndex = k == -1 ? -1 : k + currentRoute;
			float locDist = lprevious.distanceTo(location);
			dist += locDist;
			if (dist >= distanceAction) {
				if (locDist > 1) {
					double normalizedOffset = (dist - distanceAction) / locDist;
					Location projection = calculateProjection(1 - normalizedOffset, lprevious, location);
					actionPoints.add(ind, new ActionPoint(projection, actionPointIndex, normalizedOffset));
				}
				break;
			} else {
				actionPoints.add(ind, new ActionPoint(location, actionPointIndex, 0.0));
				lprevious = location;
			}
			if (prevFinishPoint == k) {
				if (ind >= 2) {
					actionPoints.remove(ind - 2);
					actionPoints.remove(ind - 2);
				}
				break;
			}
		}
	}

	private Location calculateProjection(double part, Location lp, Location l) {
		Location p = new Location(l);
		p.setLatitude(lp.getLatitude() + part * (l.getLatitude() - lp.getLatitude()));
		p.setLongitude(lp.getLongitude() + part * (l.getLongitude() - lp.getLongitude()));
		return p;
	}

	@Override
	public void onDraw(Canvas canvas, RotatedTileBox tileBox, DrawSettings settings) {
	}

	@Override
	public boolean drawInScreenPixels() {
		return false;
	}

	@Override
	public boolean onLongPressEvent(@NonNull PointF point, @NonNull RotatedTileBox tileBox) {
		return false;
	}

	@Override
	public boolean onSingleTap(@NonNull PointF point, @NonNull RotatedTileBox tileBox) {
		return false;
	}

	private int getRadiusPoi(RotatedTileBox tb) {
		double zoom = tb.getZoom();
		int r;
		if (zoom <= 15) {
			r = 8;
		} else if (zoom <= 16) {
			r = 10;
		} else if (zoom <= 17) {
			r = 14;
		} else {
			r = 18;
		}
		return (int) (r * tb.getDensity());
	}

	@Nullable
	private List<TransportStop> getRouteTransportStops() {
		return helper.isPublicTransportMode() ? publicTransportRouteGeometry.getDrawer().getRouteTransportStops() : null;
	}

	private void getFromPoint(RotatedTileBox tb, PointF point, List<? super TransportStop> res, @NonNull List<TransportStop> routeTransportStops) {
		MapRendererView mapRenderer = getMapRenderer();
		float radius = getRadiusPoi(tb) * TOUCH_RADIUS_MULTIPLIER;
		List<PointI> touchPolygon31 = null;
		if (mapRenderer != null) {
			touchPolygon31 = NativeUtilities.getPolygon31FromPixelAndRadius(mapRenderer, point, radius);
			if (touchPolygon31 == null) {
				return;
			}
		}

		try {
			for (int i = 0; i < routeTransportStops.size(); i++) {
				TransportStop transportStop = routeTransportStops.get(i);
				LatLon latLon = transportStop.getLocation();
				if (latLon == null) {
					continue;
				}

				boolean add = mapRenderer != null
						? NativeUtilities.isPointInsidePolygon(latLon, touchPolygon31)
						: tb.isLatLonNearPixel(latLon, point.x, point.y, radius);
				if (add) {
					res.add(transportStop);
				}
			}
		} catch (IndexOutOfBoundsException e) {
			// ignore
		}
	}

	private boolean isColoringAvailable(@NonNull ColoringType routeColoringType,
	                                    @Nullable String routeInfoAttribute) {
		return coloringAvailabilityCache
				.isColoringAvailable(helper.getRoute(), routeColoringType, routeInfoAttribute);
	}

	@Override
	public void collectObjectsFromPoint(PointF point, RotatedTileBox tileBox, List<Object> res,
	                                    boolean unknownLocation, boolean excludeUntouchableObjects) {
		if (excludeUntouchableObjects) {
			return;
		}

		List<TransportStop> routeTransportStops = getRouteTransportStops();
		if (!Algorithms.isEmpty(routeTransportStops)) {
			getFromPoint(tileBox, point, res, routeTransportStops);
		}
	}

	@Override
	public LatLon getObjectLocation(Object o) {
		if (o instanceof TransportStop){
			return ((TransportStop)o).getLocation();
		}
		return null;
	}

	@Override
	public PointDescription getObjectName(Object o) {
		if (o instanceof TransportStop){
			return new PointDescription(PointDescription.POINT_TYPE_TRANSPORT_STOP, getContext().getString(R.string.transport_Stop),
					((TransportStop)o).getName());
		}
		return null;
	}

	@Override
	public boolean disableSingleTap() {
		return isPreviewRouteLineVisible();
	}

	@Override
	public boolean disableLongPressOnMap(PointF point, RotatedTileBox tileBox) {
		return isPreviewRouteLineVisible();
	}

	@Override
	protected void cleanupResources() {
		super.cleanupResources();
		resetLayer();
	}

	/** OpenGL */
	private void resetLayer() {
		clearXAxisPoints();
		if (routeGeometry != null && routeGeometry.hasMapRenderer()) {
			routeGeometry.resetSymbolProviders();
		}
		if (publicTransportRouteGeometry != null && publicTransportRouteGeometry.hasMapRenderer()) {
			publicTransportRouteGeometry.resetSymbolProviders();
		}
		lastRouteProjection = null;
	}

	public void resetColorAvailabilityCache() {
		coloringAvailabilityCache.resetCache();
	}

	private static class RenderState {
		private Location lastProjection = null;
		private int startLocationIndex = -1;
		private int publicTransportRoute = -1;
		private ColoringType coloringType = ColoringType.DEFAULT;
		private int routeColor = -1;
		private float routeWidth = -1f;
		private int currentRoute = -1;
		private int zoom = -1;
		private boolean shouldShowTurnArrows;
		private boolean shouldShowDirectionArrows;

		boolean shouldRebuildRoute;
		boolean shouldRebuildTransportRoute;
		boolean shouldUpdateRoute;
		boolean shouldUpdateActionPoints;

		public void updateRouteState(@Nullable Location lastProjection, int startLocationIndex,
		                             ColoringType coloringType, int routeColor, float routeWidth,
		                             int currentRoute, int zoom,
		                             boolean shouldShowTurnArrows, boolean shouldShowDirectionArrows) {
			this.shouldRebuildRoute = this.coloringType != coloringType
					|| this.routeColor != routeColor
					|| this.routeWidth != routeWidth
					|| this.shouldShowDirectionArrows != shouldShowDirectionArrows;

			this.shouldUpdateRoute = (!MapUtils.areLatLonEqual(this.lastProjection, lastProjection, HIGH_LATLON_PRECISION)
					|| this.startLocationIndex != startLocationIndex)
					&& this.coloringType == coloringType
					&& this.routeColor == routeColor
					&& this.routeWidth == routeWidth
					&& this.shouldShowDirectionArrows == shouldShowDirectionArrows;

			this.shouldUpdateActionPoints = this.shouldRebuildRoute
					|| this.shouldUpdateRoute
					|| this.shouldShowTurnArrows != shouldShowTurnArrows
					|| this.currentRoute != currentRoute
					|| this.zoom != zoom;

			this.lastProjection = lastProjection;
			this.startLocationIndex = startLocationIndex;
			this.coloringType = coloringType;
			this.routeColor = routeColor;
			this.routeWidth = routeWidth;
			this.currentRoute = currentRoute;
			this.zoom = zoom;
			this.shouldShowTurnArrows = shouldShowTurnArrows;
			this.shouldShowDirectionArrows = shouldShowDirectionArrows;
		}

		public void updateTransportRouteState(int publicTransportRoute) {
			this.shouldRebuildTransportRoute = this.publicTransportRoute != publicTransportRoute;

			this.publicTransportRoute = publicTransportRoute;
		}
	}

	public static class ActionPoint {

		@NonNull
		public final Location location;

		public final int index;
		public final double normalizedOffset;

		public ActionPoint(@NonNull Location location, int index, double normalizedOffset) {
			this.location = location;
			this.index = index;
			this.normalizedOffset = normalizedOffset;
		}
	}
}