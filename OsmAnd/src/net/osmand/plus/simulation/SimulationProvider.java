package net.osmand.plus.simulation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.binary.RouteDataObject;
import net.osmand.data.QuadPointDouble;
import net.osmand.plus.routing.RouteSegmentSearchResult;
import net.osmand.router.RouteSegmentResult;
import net.osmand.util.MapUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SimulationProvider {
	public static final org.apache.commons.logging.Log LOG = PlatformUtil.getLog(SimulationProvider.class);

	public static final String SIMULATED_PROVIDER = "OsmAnd";
	public static final String SIMULATED_PROVIDER_GPX = "GPX";
	public static final String SIMULATED_PROVIDER_TUNNEL = "TUNNEL";

	private final Location startLocation;
	private final List<RouteSegmentResult> roads;

	private int currentRoad = -1;
	private int currentSegment;
	private QuadPointDouble currentPoint;

	public SimulationProvider(@NonNull Location location, @NonNull List<RouteSegmentResult> roads) {
		this.startLocation = new Location(location);
		this.roads = new ArrayList<>(roads);
	}

	public void startSimulation() {
		long time = System.currentTimeMillis();
		if (time - startLocation.getTime() > 5000 || time < startLocation.getTime()) {
			startLocation.setTime(time);
		}
		RouteSegmentSearchResult searchResult = RouteSegmentSearchResult.searchRouteSegment(
				startLocation.getLatitude(), startLocation.getLongitude(), -1, roads);
		if (searchResult != null) {
			currentRoad = searchResult.getRoadIndex();
			currentSegment = searchResult.getSegmentIndex();
			currentPoint = searchResult.getPoint();
		} else {
			currentRoad = -1;
		}
	}

	private double proceedMeters(double meters, Location location) {
		if (currentRoad == -1) {
			return -1;
		}
		for (int i = currentRoad; i < roads.size(); i++) {
			RouteSegmentResult road = roads.get(i);
			boolean firstRoad = i == currentRoad;
			int increment = road.getStartPointIndex() < road.getEndPointIndex() ? +1 : -1;
			for (int j = firstRoad ? currentSegment : (road.getStartPointIndex() + increment);
			     increment > 0 ? j <= road.getEndPointIndex() : j >= road.getEndPointIndex();
			     j += increment) {
				RouteDataObject obj = road.getObject();
				int st31x = obj.getPoint31XTile(j - increment);
				int st31y = obj.getPoint31YTile(j - increment);
				int end31x = obj.getPoint31XTile(j);
				int end31y = obj.getPoint31YTile(j);
				boolean last = i == roads.size() - 1 && j == road.getEndPointIndex();
				boolean first = firstRoad && j == currentSegment;
				if (first) {
					st31x = (int) currentPoint.x;
					st31y = (int) currentPoint.y;
				}
				double dd = MapUtils.measuredDist31(st31x, st31y, end31x, end31y);
				if (meters > dd && !last) {
					meters -= dd;
				} else if (dd > 0) {
					int prx = (int) (st31x + (end31x - st31x) * (meters / dd));
					int pry = (int) (st31y + (end31y - st31y) * (meters / dd));
					if (prx == 0 || pry == 0) {
						LOG.error(String.format(Locale.US, "proceedMeters zero x or y (%d,%d) (%s)", prx, pry, road));
						return -1;
					}
					location.setLongitude(MapUtils.get31LongitudeX(prx));
					location.setLatitude(MapUtils.get31LatitudeY(pry));
					return Math.max(meters - dd, 0);
				} else {
					LOG.error(String.format(Locale.US,
							"proceedMeters break at the end of the road (sx=%d, sy=%d) (%s)", st31x, st31y, road));
					break;
				}
			}
		}
		return -1;
	}

	/**
	 * @return null if it is not available of far from boundaries
	 */
	@Nullable
	public Location getSimulatedLocationForTunnel() {
		if (!isSimulatedDataAvailable()) {
			return null;
		}

		Location location = new Location(SIMULATED_PROVIDER_TUNNEL);
		location.setSpeed(startLocation.getSpeed());
		location.setAltitude(startLocation.getAltitude());
		location.setTime(System.currentTimeMillis());
		double meters = startLocation.getSpeed() * ((System.currentTimeMillis() - startLocation.getTime()) / 1000.0);
		double proc = proceedMeters(meters, location);
		if (proc < 0 || proc >= 100) {
			return null;
		}
		return location;
	}

	public boolean isSimulatedDataAvailable() {
		return startLocation.getSpeed() > 0 && currentRoad >= 0;
	}

	public static boolean isNotSimulatedLocation(@Nullable Location location) {
		if (location != null) {
			return !(SIMULATED_PROVIDER.equals(location.getProvider())
					|| SIMULATED_PROVIDER_GPX.equals(location.getProvider())
					|| SIMULATED_PROVIDER_TUNNEL.equals(location.getProvider()));
		}
		return true;
	}

	public static boolean isTunnelLocationSimulated(@Nullable Location location) {
		return location != null && SIMULATED_PROVIDER_TUNNEL.equals(location.getProvider());
	}
}
