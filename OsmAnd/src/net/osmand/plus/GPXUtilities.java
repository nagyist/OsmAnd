package net.osmand.plus;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.TimeZone;

import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.content.Context;
import android.graphics.Color;

public class GPXUtilities {
	public final static Log log = PlatformUtil.getLog(GPXUtilities.class);

	private final static String GPX_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"; //$NON-NLS-1$

	private final static NumberFormat latLonFormat = new DecimalFormat("0.00#####", new DecimalFormatSymbols(
			new Locale("EN", "US")));

	public static class GPXExtensions {
		Map<String, String> extensions = null;

		public Map<String, String> getExtensionsToRead() {
			if (extensions == null) {
				return Collections.emptyMap();
			}
			return extensions;
		}
		
		public int getColor(int defColor) {
			if(extensions != null && extensions.containsKey("color")) {
				try {
					return Color.parseColor(extensions.get("color").toUpperCase());
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				}
			}
			return defColor;
		}
		
		public void setColor(int color) {
			getExtensionsToWrite().put("color", Algorithms.colorToString(color));
		}

		public Map<String, String> getExtensionsToWrite() {
			if (extensions == null) {
				extensions = new LinkedHashMap<String, String>();
			}
			return extensions;
		}

	}

	public static class WptPt extends GPXExtensions {
		public double lat;
		public double lon;
		public String name = null;
		public String category = null;
		public String desc = null;
		// by default
		public long time = 0;
		public double ele = Double.NaN;
		public double speed = 0;
		public double hdop = Double.NaN;

		public WptPt() {
		}
		

		public WptPt(double lat, double lon, long time, double ele, double speed, double hdop) {
			this.lat = lat;
			this.lon = lon;
			this.time = time;
			this.ele = ele;
			this.speed = speed;
			this.hdop = hdop;
		}

	}

	public static class TrkSegment extends GPXExtensions {
		public List<WptPt> points = new ArrayList<WptPt>();
		
		public List<GPXTrackAnalysis> splitByDistance(double meters) {
			return split(getDistanceMetric(), meters);
		}
		
		public List<GPXTrackAnalysis> splitByTime(int seconds) {
			return split(getTimeSplit(), seconds);
		}
		
		private List<GPXTrackAnalysis> split(SplitMetric metric, double metricLimit) {
			List<SplitSegment> splitSegments = new ArrayList<GPXUtilities.SplitSegment>();
			splitSegment(metric, metricLimit, splitSegments, this);
			return convert(splitSegments);
		}

	}

	public static class Track extends GPXExtensions {
		public String name = null;
		public String desc = null;
		public List<TrkSegment> segments = new ArrayList<TrkSegment>();

	}

	public static class Route extends GPXExtensions {
		public String name = null;
		public String desc = null;
		public List<WptPt> points = new ArrayList<WptPt>();

	}
	
	public static class GPXTrackAnalysis {
		public float totalDistance = 0;
		public int totalTracks = 0;
		public long startTime = Long.MAX_VALUE;
		public long endTime = Long.MIN_VALUE;
		public long timeSpan = 0;
		public long timeMoving = 0;
		public float totalDistanceMoving = 0;

		public double diffElevationUp = 0;
		public double diffElevationDown = 0;
		public double avgElevation = 0;
		public double minElevation = 99999;
		public double maxElevation = -100;
		
		public float maxSpeed = 0;
		public float avgSpeed;
		
		public int points;
		public int wptPoints = 0;
		
		public double metricEnd;

		public boolean isTimeSpecified() {
			return startTime != Long.MAX_VALUE && startTime != 0;
		}
		
		public boolean isTimeMoving() {
			return timeMoving != 0;
		}
		
		public boolean isElevationSpecified() {
			return maxElevation != -100;
		}
		
		public int getTimeHours(long time) {
			return (int) ((time / 1000) / 3600);
		}
		
		
		public int getTimeSeconds(long time) {
			return (int) ((time / 1000) % 60);
		}

		public int getTimeMinutes(long time) {
			return (int) (((time / 1000) / 60) % 60);
		}
		
		public boolean isSpeedSpecified() {
			return avgSpeed > 0;
		}
		
		
		public static GPXTrackAnalysis segment(long filetimestamp, TrkSegment segment) {
			return new GPXTrackAnalysis().prepareInformation(filetimestamp, new SplitSegment(segment));
		}
		
		public GPXTrackAnalysis prepareInformation(long filestamp, SplitSegment... splitSegments) {
			float[] calculations = new float[1];

			float totalElevation = 0;
			int elevationPoints = 0;
			int speedCount = 0;
			double totalSpeedSum = 0;
			points = 0;
			for (SplitSegment s : splitSegments) {
				final int numberOfPoints = s.getNumberOfPoints();
				metricEnd += s.metricEnd;
				points += numberOfPoints;
				for (int j = 0; j < numberOfPoints; j++) {
					WptPt point = s.get(j);
					long time = point.time;
					if (time != 0) {
						startTime = Math.min(startTime, time);
						endTime = Math.max(endTime, time);
					}

					double elevation = point.ele;
					if (!Double.isNaN(elevation)) {
						totalElevation += elevation;
						elevationPoints++;
						minElevation = Math.min(elevation, minElevation);
						maxElevation = Math.max(elevation, maxElevation);
					}

					float speed = (float) point.speed;
					if (speed > 0) {
						totalSpeedSum += speed;
						maxSpeed = Math.max(speed, maxSpeed);
						speedCount++;
					}

					if (j > 0) {
						WptPt prev = s.get(j - 1);

						if (!Double.isNaN(point.ele) && !Double.isNaN(prev.ele)) {
							double diff = point.ele - prev.ele;
							if (diff > 0) {
								diffElevationUp += diff;
							} else {
								diffElevationDown -= diff;
							}
						}

						// totalDistance += MapUtils.getDistance(prev.lat, prev.lon, point.lat, point.lon);
						// using ellipsoidal 'distanceBetween' instead of spherical haversine (MapUtils.getDistance) is
						// a little more exact, also seems slightly faster:
						net.osmand.Location.distanceBetween(prev.lat, prev.lon, point.lat, point.lon, calculations);
						totalDistance += calculations[0];

						// Averaging speed values is less exact than totalDistance/timeMoving
						if (speed > 0 && point.time != 0 && prev.time != 0) {
							timeMoving = timeMoving + (point.time - prev.time);
							totalDistanceMoving += calculations[0];
						}
					}
				}
			}
			if(!isTimeSpecified()){
				startTime = filestamp;
				endTime = filestamp;
			}

			// OUTPUT:
			// 1. Total distance, Start time, End time
			// 2. Time span
			timeSpan = endTime - startTime;

			// 3. Time moving, if any
			// 4. Elevation, eleUp, eleDown, if recorded
			if (elevationPoints > 0) {
				avgElevation =  totalElevation / elevationPoints;
			}



			// 5. Max speed and Average speed, if any. Average speed is NOT overall (effective) speed, but only calculated for "moving" periods.
			if(speedCount > 0) {
				if(timeMoving > 0){
					avgSpeed = (float) (totalDistanceMoving / timeMoving * 1000);
				} else {
					avgSpeed = (float) (totalSpeedSum / speedCount);
				}
			} else {
				avgSpeed = -1;
			}
			return this;
		}
		
	}
	
	private static class SplitSegment {
		TrkSegment  segment;
		double startCoeff = 0;
		int startPointInd;
		double endCoeff = 0;
		int endPointInd;
		double metricEnd;
		
		public SplitSegment(TrkSegment s) {
			startPointInd = 0;
			startCoeff = 0;
			endPointInd = s.points.size() - 2;
			endCoeff = 1;
			this.segment = s;
		}
		
		public SplitSegment(TrkSegment s, int pointInd, double cf) {
			this.segment = s;
			this.startPointInd = pointInd;
			this.startCoeff = cf;
		}
		
		
		public int getNumberOfPoints() {
			return endPointInd - startPointInd + 2;
		}
		
		public WptPt get(int j) {
			final int ind = j + startPointInd;
			if(j == 0) {
				if(startCoeff == 0) {
					return segment.points.get(ind);
				}
				return approx(segment.points.get(ind), segment.points.get(ind + 1), startCoeff);
			}
			if(j == getNumberOfPoints() - 1) {
				if(endCoeff == 1) {
					return segment.points.get(ind);
				}
				return approx(segment.points.get(ind - 1), segment.points.get(ind), endCoeff);
			}
			return segment.points.get(ind);
		}

		
		private WptPt approx(WptPt w1, WptPt w2, double cf) {
			long time = value(w1.time, w2.time, 0, cf);
			double speed = value(w1.speed, w2.speed, 0, cf);
			double ele = value(w1.ele, w2.ele, 0, cf);
			double hdop = value(w1.hdop, w2.hdop, 0, cf);
			double lat = value(w1.lat, w2.lat, -360, cf);
			double lon = value(w1.lon, w2.lon, -360, cf);
			return new WptPt(lat, lon, time, ele, speed, hdop);
		}
		
		private double value(double vl, double vl2, double none, double cf) {
			if(vl == none || Double.isNaN(vl)) {
				return vl2;
			} else if (vl2 == none || Double.isNaN(vl2)) {
				return vl;
			}
			return vl + cf * (vl2 - vl);
		}

		private long value(long vl, long vl2, long none, double cf) {
			if(vl == none) {
				return vl2;
			} else if(vl2 == none) {
				return vl;
			}
			return vl + ((long) (cf * (vl2 - vl)));
		}

	
		public double setLastPoint(int pointInd, double endCf) {
			endCoeff = endCf;
			endPointInd = pointInd;
			return endCoeff;
		}
		
	}
	
	private static SplitMetric getDistanceMetric() {
		return new SplitMetric() {
			
			private float[] calculations = new float[1];

			@Override
			public double metric(WptPt p1, WptPt p2) {
				net.osmand.Location.distanceBetween(p1.lat, p1.lon, p2.lat, p2.lon, calculations);
				return calculations[0];
			}
		};
	}
	
	private static SplitMetric getTimeSplit() {
		return new SplitMetric() {
			
			@Override
			public double metric(WptPt p1, WptPt p2) {
				if(p1.time != 0 && p2.time != 0) {
					return (int) Math.abs((p2.time - p1.time) / 1000l);
				}
				return 0;
			}
		};
	}
	
	private abstract static class SplitMetric {

		public abstract double metric(WptPt p1, WptPt p2);

	}
	
	private static void splitSegment(SplitMetric metric, double metricLimit, List<SplitSegment> splitSegments,
			TrkSegment segment) {
		double currentMetricEnd = metricLimit;
		SplitSegment sp = new SplitSegment(segment, 0, 0);
		double total = 0;
		WptPt prev = null ;
		for (int k = 0; k < segment.points.size(); k++) {
			WptPt point = segment.points.get(k);
			if (k > 0) {
				double currentSegment = metric.metric(prev, point);
				while (total + currentSegment > currentMetricEnd) {
					double p = currentMetricEnd - total;
					double cf = (p / currentSegment); 
					sp.setLastPoint(k - 1, cf);
					sp.metricEnd = currentMetricEnd;
					splitSegments.add(sp);
					
					sp = new SplitSegment(segment, k - 1, cf);
					currentMetricEnd += metricLimit;
					prev = sp.get(0);
				}
				total += currentSegment;
			}
			prev = point;
		}
		if (segment.points.size() > 0
				&& !(sp.endPointInd == segment.points.size() - 1 && sp.startCoeff == 1)) {
			sp.metricEnd = total;
			sp.setLastPoint(segment.points.size() - 2, 1);
			splitSegments.add(sp);
		}
	}

	private static List<GPXTrackAnalysis> convert(List<SplitSegment> splitSegments) {
		List<GPXTrackAnalysis> ls = new ArrayList<GPXUtilities.GPXTrackAnalysis>();
		for(SplitSegment s : splitSegments) {
			GPXTrackAnalysis a = new GPXTrackAnalysis();
			a.prepareInformation(0, s);
			ls.add(a);
		}
		return ls;
	}
	
	public static class GPXFile extends GPXExtensions {
		public String author;
		public List<Track> tracks = new ArrayList<Track>();
		public List<WptPt> points = new ArrayList<WptPt>();
		public List<Route> routes = new ArrayList<Route>();
		public String warning = null;
		public String path = "";
		public boolean showCurrentTrack;

		public boolean isCloudmadeRouteFile() {
			return "cloudmade".equalsIgnoreCase(author);
		}
		
		
		public GPXTrackAnalysis getAnalysis(long fileTimestamp) {
			GPXTrackAnalysis g = new GPXTrackAnalysis();
			g.wptPoints = points.size();
			List<SplitSegment> splitSegments = new ArrayList<GPXUtilities.SplitSegment>();
			for(int i = 0; i< tracks.size() ; i++){
				Track subtrack = tracks.get(i);
				for(TrkSegment segment : subtrack.segments){
					g.totalTracks ++;
					if(segment.points.size() > 1) {
						splitSegments.add(new SplitSegment(segment));
					}
				}
			}
			g.prepareInformation(fileTimestamp, splitSegments.toArray(new SplitSegment[splitSegments.size()]));
			return g ;
		}
		
		
		public List<GPXTrackAnalysis> splitByDistance(int meters) {
			return split(getDistanceMetric(), meters);
		}
		
		public List<GPXTrackAnalysis> splitByTime(int seconds) {
			return split(getTimeSplit(), seconds);
		}


		public List<GPXTrackAnalysis> split(SplitMetric metric, int metricLimit) {
			List<SplitSegment> splitSegments = new ArrayList<GPXUtilities.SplitSegment>();
			for(int i = 0; i< tracks.size() ; i++){
				Track subtrack = tracks.get(i);
				for (int j = 0; j < subtrack.segments.size(); j++) {
					TrkSegment segment = subtrack.segments.get(j);
					splitSegment(metric, metricLimit, splitSegments, segment);
				}
			}
			return convert(splitSegments);
		}


		
		public boolean hasRtePt() {
			for(Route r : routes) {
				if(r.points.size() > 0) {
					return true;
				}
			}
			return false;
		}
		
		public boolean hasTrkpt() {
			for(Track t  : tracks) {
				for (TrkSegment ts : t.segments) {
					if (ts.points.size() > 0) {
						return true;
					}
				}
			}
			return false;
		}
		
		public List<List<WptPt>> processRoutePoints() {
			List<List<WptPt>> tpoints = new ArrayList<List<WptPt>>();
			if (routes.size() > 0) {
				for (Route r : routes) {
					tpoints.add(r.points);
				}
			}
			return tpoints;
		}

		public List<List<WptPt>> proccessPoints() {
			List<List<WptPt>> tpoints = new ArrayList<List<WptPt>>();
			for (Track t : tracks) {
				for (TrkSegment ts : t.segments) {
					if (ts.points.size() > 0) {
						tpoints.add(ts.points);
					}
				}
			}
			return tpoints;
		}

		public WptPt findPointToShow() {
			for (Track t : tracks) {
				for (TrkSegment s : t.segments) {
					if (s.points.size() > 0) {
						return s.points.get(0);
					}
				}
			}
			for (Route s : routes) {
				if (s.points.size() > 0) {
					return s.points.get(0);
				}
			}
			if (points.size() > 0) {
				return points.get(0);
			}
			return null;
		}

		public boolean isEmpty() {
			for (Track t : tracks) {
				if (t.segments != null) {
					for (TrkSegment s : t.segments) {
						boolean tracksEmpty = s.points.isEmpty();
						if (!tracksEmpty) {
							return false;
						}
					}
				}
			}
			return points.isEmpty() && routes.isEmpty();
		}

	}

    public static String asString(GPXFile file, OsmandApplication ctx) {
           final Writer writer = new StringWriter();
           GPXUtilities.writeGpx(writer, file, ctx);
           return writer.toString();
       }

	public static String writeGpxFile(File fout, GPXFile file, OsmandApplication ctx) {
		Writer output = null;
		try {
			output = new OutputStreamWriter(new FileOutputStream(fout), "UTF-8"); //$NON-NLS-1$
			return writeGpx(output, file, ctx);
		} catch (IOException e) {
			log.error("Error saving gpx", e); //$NON-NLS-1$
			return ctx.getString(R.string.error_occurred_saving_gpx);
		} finally {
			if (output != null) {
				try {
					output.close();
				} catch (IOException ignore) {
					// ignore
				}
			}
		}
	}

	public static String writeGpx(Writer output, GPXFile file, OsmandApplication ctx) {
		try {
			SimpleDateFormat format = new SimpleDateFormat(GPX_TIME_FORMAT);
			format.setTimeZone(TimeZone.getTimeZone("UTC"));
			XmlSerializer serializer = PlatformUtil.newSerializer();
			serializer.setOutput(output);
			serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true); //$NON-NLS-1$
			serializer.startDocument("UTF-8", true); //$NON-NLS-1$
			serializer.startTag(null, "gpx"); //$NON-NLS-1$
			serializer.attribute(null, "version", "1.1"); //$NON-NLS-1$ //$NON-NLS-2$
			if (file.author == null) {
				serializer.attribute(null, "creator", Version.getAppName(ctx)); //$NON-NLS-1$
			} else {
				serializer.attribute(null, "creator", file.author); //$NON-NLS-1$
			}
			serializer.attribute(null, "xmlns", "http://www.topografix.com/GPX/1/1"); //$NON-NLS-1$ //$NON-NLS-2$
			serializer.attribute(null, "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			serializer.attribute(null, "xsi:schemaLocation",
					"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd");

			for (Track track : file.tracks) {
				serializer.startTag(null, "trk"); //$NON-NLS-1$
				writeNotNullText(serializer, "name", track.name);
				writeNotNullText(serializer, "desc", track.desc);
				for (TrkSegment segment : track.segments) {
					serializer.startTag(null, "trkseg"); //$NON-NLS-1$
					for (WptPt p : segment.points) {
						serializer.startTag(null, "trkpt"); //$NON-NLS-1$
						writeWpt(format, serializer, p);
						serializer.endTag(null, "trkpt"); //$NON-NLS-1$
					}
					serializer.endTag(null, "trkseg"); //$NON-NLS-1$
				}
				writeExtensions(serializer, track);
				serializer.endTag(null, "trk"); //$NON-NLS-1$
			}

			for (Route track : file.routes) {
				serializer.startTag(null, "rte"); //$NON-NLS-1$
				writeNotNullText(serializer, "name", track.name);
				writeNotNullText(serializer, "desc", track.desc);

				for (WptPt p : track.points) {
					serializer.startTag(null, "rtept"); //$NON-NLS-1$
					writeWpt(format, serializer, p);
					serializer.endTag(null, "rtept"); //$NON-NLS-1$
				}
				writeExtensions(serializer, track);
				serializer.endTag(null, "rte"); //$NON-NLS-1$
			}

			for (WptPt l : file.points) {
				serializer.startTag(null, "wpt"); //$NON-NLS-1$
				writeWpt(format, serializer, l);
				serializer.endTag(null, "wpt"); //$NON-NLS-1$
			}

			serializer.endTag(null, "gpx"); //$NON-NLS-1$
			serializer.flush();
			serializer.endDocument();
		} catch (RuntimeException e) {
			log.error("Error saving gpx", e); //$NON-NLS-1$
			return ctx.getString(R.string.error_occurred_saving_gpx);
		} catch (IOException e) {
			log.error("Error saving gpx", e); //$NON-NLS-1$
			return ctx.getString(R.string.error_occurred_saving_gpx);
		}
		return null;
	}

	private static void writeNotNullText(XmlSerializer serializer, String tag, String value) throws IOException {
		if (value != null) {
			serializer.startTag(null, tag);
			serializer.text(value);
			serializer.endTag(null, tag);
		}
	}

	private static void writeExtensions(XmlSerializer serializer, GPXExtensions p) throws IOException {
		if (!p.getExtensionsToRead().isEmpty()) {
			serializer.startTag(null, "extensions");
			for (Map.Entry<String, String> s : p.getExtensionsToRead().entrySet()) {
				writeNotNullText(serializer, s.getKey(), s.getValue());
			}
			serializer.endTag(null, "extensions");
		}
	}

	private static void writeWpt(SimpleDateFormat format, XmlSerializer serializer, WptPt p) throws IOException {
		serializer.attribute(null, "lat", latLonFormat.format(p.lat)); //$NON-NLS-1$ //$NON-NLS-2$
		serializer.attribute(null, "lon", latLonFormat.format(p.lon)); //$NON-NLS-1$ //$NON-NLS-2$

		if (!Double.isNaN(p.ele)) {
			writeNotNullText(serializer, "ele", p.ele + "");
		}
		writeNotNullText(serializer, "name", p.name);
		writeNotNullText(serializer, "category", p.category);
		writeNotNullText(serializer, "desc", p.desc);
		if (!Double.isNaN(p.hdop)) {
			writeNotNullText(serializer, "hdop", p.hdop + "");
		}
		if (p.time != 0) {
			writeNotNullText(serializer, "time", format.format(new Date(p.time)));
		}
		if (p.speed > 0) {
			p.getExtensionsToWrite().put("speed", p.speed + "");
		}
		writeExtensions(serializer, p);
	}

	public static class GPXFileResult {
		public ArrayList<List<Location>> locations = new ArrayList<List<Location>>();
		public ArrayList<WptPt> wayPoints = new ArrayList<WptPt>();
		// special case for cloudmate gpx : they discourage common schema
		// by using waypoint as track points and rtept are not very close to real way
		// such as wpt. However they provide additional information into gpx.
		public boolean cloudMadeFile;
		public String error;

		public Location findFistLocation() {
			for (List<Location> l : locations) {
				for (Location ls : l) {
					if (ls != null) {
						return ls;
					}
				}
			}
			return null;
		}
	}

	private static String readText(XmlPullParser parser, String key) throws XmlPullParserException, IOException {
		int tok;
		String text = null;
		while ((tok = parser.next()) != XmlPullParser.END_DOCUMENT) {
			if (tok == XmlPullParser.END_TAG && parser.getName().equals(key)) {
				break;
			} else if (tok == XmlPullParser.TEXT) {
				if (text == null) {
					text = parser.getText();
				} else {
					text += parser.getText();
				}
			}

		}
		return text;
	}

	public static GPXFile loadGPXFile(Context ctx, File f) {
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(f);
			GPXFile file = loadGPXFile(ctx, fis);
			file.path = f.getAbsolutePath();
			try {
				fis.close();
			} catch (IOException e) {
			}
			return file;
		} catch (FileNotFoundException e) {
			GPXFile res = new GPXFile();
			res.path = f.getAbsolutePath();
			log.error("Error reading gpx", e); //$NON-NLS-1$
			res.warning = ctx.getString(R.string.error_reading_gpx);
			return res;
		} finally {
			try {
				if (fis != null)
					fis.close();
			} catch (IOException ignore) {
				// ignore
			}
		}
	}

	public static GPXFile loadGPXFile(Context ctx, InputStream f) {
		GPXFile res = new GPXFile();
		SimpleDateFormat format = new SimpleDateFormat(GPX_TIME_FORMAT);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		try {
			XmlPullParser parser = PlatformUtil.newXMLPullParser();
			parser.setInput(getUTF8Reader(f)); //$NON-NLS-1$
			Stack<GPXExtensions> parserState = new Stack<GPXExtensions>();
			boolean extensionReadMode = false;
			parserState.push(res);
			int tok;
			while ((tok = parser.next()) != XmlPullParser.END_DOCUMENT) {
				if (tok == XmlPullParser.START_TAG) {
					Object parse = parserState.peek();
					String tag = parser.getName();
					if (extensionReadMode && parse instanceof GPXExtensions) {
						String value = readText(parser, tag);
						if (value != null) {
							((GPXExtensions) parse).getExtensionsToWrite().put(tag, value);
							if (tag.equals("speed") && parse instanceof WptPt) {
								try {
									((WptPt) parse).speed = Float.parseFloat(value);
								} catch (NumberFormatException e) {
								}
							}
						}

					} else if (parse instanceof GPXExtensions && tag.equals("extensions")) {
						extensionReadMode = true;
					} else {
						if (parse instanceof GPXFile) {
							if (parser.getName().equals("gpx")) {
								((GPXFile) parse).author = parser.getAttributeValue("", "creator");
							}
							if (parser.getName().equals("trk")) {
								Track track = new Track();
								((GPXFile) parse).tracks.add(track);
								parserState.push(track);
							}
							if (parser.getName().equals("rte")) {
								Route route = new Route();
								((GPXFile) parse).routes.add(route);
								parserState.push(route);
							}
							if (parser.getName().equals("wpt")) {
								WptPt wptPt = parseWptAttributes(parser);
								((GPXFile) parse).points.add(wptPt);
								parserState.push(wptPt);
							}
						} else if (parse instanceof Route) {
							if (parser.getName().equals("name")) {
								((Route) parse).name = readText(parser, "name");
							}
							if (parser.getName().equals("desc")) {
								((Route) parse).desc = readText(parser, "desc");
							}
							if (parser.getName().equals("rtept")) {
								WptPt wptPt = parseWptAttributes(parser);
								((Route) parse).points.add(wptPt);
								parserState.push(wptPt);
							}
						} else if (parse instanceof Track) {
							if (parser.getName().equals("name")) {
								((Track) parse).name = readText(parser, "name");
							}
							if (parser.getName().equals("desc")) {
								((Track) parse).desc = readText(parser, "desc");
							}
							if (parser.getName().equals("trkseg")) {
								TrkSegment trkSeg = new TrkSegment();
								((Track) parse).segments.add(trkSeg);
								parserState.push(trkSeg);
							}
						} else if (parse instanceof TrkSegment) {
							if (parser.getName().equals("trkpt")) {
								WptPt wptPt = parseWptAttributes(parser);
								((TrkSegment) parse).points.add(wptPt);
								parserState.push(wptPt);
							}
							// main object to parse
						} else if (parse instanceof WptPt) {
							if (parser.getName().equals("name")) {
								((WptPt) parse).name = readText(parser, "name");
							} else if (parser.getName().equals("desc")) {
								((WptPt) parse).desc = readText(parser, "desc");
							} else if (tag.equals("category")) {
								((WptPt) parse).category = readText(parser, "category");
							} else if (parser.getName().equals("ele")) {
								String text = readText(parser, "ele");
								if (text != null) {
									try {
										((WptPt) parse).ele = Float.parseFloat(text);
									} catch (NumberFormatException e) {
									}
								}
							} else if (parser.getName().equals("hdop")) {
								String text = readText(parser, "hdop");
								if (text != null) {
									try {
										((WptPt) parse).hdop = Float.parseFloat(text);
									} catch (NumberFormatException e) {
									}
								}
							} else if (parser.getName().equals("time")) {
								String text = readText(parser, "time");
								if (text != null) {
									try {
										((WptPt) parse).time = format.parse(text).getTime();
									} catch (ParseException e) {
									}
								}
							}
						}
					}

				} else if (tok == XmlPullParser.END_TAG) {
					Object parse = parserState.peek();
					String tag = parser.getName();
					if (parse instanceof GPXExtensions && tag.equals("extensions")) {
						extensionReadMode = false;
					}

					if (tag.equals("trkpt")) {
						Object pop = parserState.pop();
						assert pop instanceof WptPt;
					} else if (tag.equals("wpt")) {
						Object pop = parserState.pop();
						assert pop instanceof WptPt;
					} else if (tag.equals("rtept")) {
						Object pop = parserState.pop();
						assert pop instanceof WptPt;
					} else if (tag.equals("trk")) {
						Object pop = parserState.pop();
						assert pop instanceof Track;
					} else if (tag.equals("rte")) {
						Object pop = parserState.pop();
						assert pop instanceof Route;
					} else if (tag.equals("trkseg")) {
						Object pop = parserState.pop();
						assert pop instanceof TrkSegment;
					}
				}
			}
//			if (convertCloudmadeSource && res.isCloudmadeRouteFile()) {
//				Track tk = new Track();
//				res.tracks.add(tk);
//				TrkSegment segment = new TrkSegment();
//				tk.segments.add(segment);
//
//				for (WptPt wp : res.points) {
//					segment.points.add(wp);
//				}
//				res.points.clear();
//			}
		} catch (RuntimeException e) {
			log.error("Error reading gpx", e); //$NON-NLS-1$
			res.warning = ctx.getString(R.string.error_reading_gpx) + " " + e.getMessage();
		} catch (XmlPullParserException e) {
			log.error("Error reading gpx", e); //$NON-NLS-1$
			res.warning = ctx.getString(R.string.error_reading_gpx) + " " + e.getMessage();
		} catch (IOException e) {
			log.error("Error reading gpx", e); //$NON-NLS-1$
			res.warning = ctx.getString(R.string.error_reading_gpx) + " " + e.getMessage();
		}

		return res;
	}

	private static Reader getUTF8Reader(InputStream f) throws IOException {
		BufferedInputStream bis = new BufferedInputStream(f);
		assert bis.markSupported();
		bis.mark(3);
		boolean reset = true;
		byte[] t = new byte[3];
		bis.read(t);
		if (t[0] == ((byte) 0xef) && t[1] == ((byte) 0xbb) && t[2] == ((byte) 0xbf)) {
			reset = false;
		}
		if (reset) {
			bis.reset();
		}
		return new InputStreamReader(bis, "UTF-8");
	}

	private static WptPt parseWptAttributes(XmlPullParser parser) {
		WptPt wpt = new WptPt();
		try {
			wpt.lat = Double.parseDouble(parser.getAttributeValue("", "lat")); //$NON-NLS-1$ //$NON-NLS-2$
			wpt.lon = Double.parseDouble(parser.getAttributeValue("", "lon")); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (NumberFormatException e) {
		}
		return wpt;
	}

	public static void mergeGPXFileInto(GPXFile to, GPXFile from) {
		if (from == null) {
			return;
		}
		if (from.showCurrentTrack) {
			to.showCurrentTrack = true;
		}
		if (from.points != null) {
			to.points.addAll(from.points);
		}
		if (from.tracks != null) {
			to.tracks.addAll(from.tracks);
		}
		if (from.routes != null) {
			to.routes.addAll(from.routes);
		}
		if (from.warning != null) {
			to.warning = from.warning;
		}

	}

}
