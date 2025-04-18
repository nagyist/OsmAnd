package net.osmand.plus.configmap.tracks;

import static com.jwetherell.openmap.common.LatLonPoint.EQUIVALENT_TOLERANCE;
import static net.osmand.plus.settings.enums.TracksSortMode.DATE_DESCENDING;
import static net.osmand.plus.settings.enums.TracksSortMode.DISTANCE_ASCENDING;
import static net.osmand.plus.settings.enums.TracksSortMode.DURATION_ASCENDING;
import static net.osmand.plus.settings.enums.TracksSortMode.LAST_MODIFIED;
import static net.osmand.plus.settings.enums.TracksSortMode.NAME_ASCENDING;
import static net.osmand.plus.settings.enums.TracksSortMode.NAME_DESCENDING;
import static net.osmand.shared.gpx.GpxParameter.FILE_CREATION_TIME;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.Collator;
import net.osmand.OsmAndCollator;
import net.osmand.plus.shared.SharedUtil;
import net.osmand.data.LatLon;
import net.osmand.plus.myplaces.tracks.VisibleTracksGroup;
import net.osmand.plus.settings.enums.TracksSortMode;
import net.osmand.shared.gpx.data.ComparableTracksGroup;
import net.osmand.shared.data.KLatLon;
import net.osmand.shared.gpx.GpxDataItem;
import net.osmand.shared.gpx.GpxTrackAnalysis;
import net.osmand.shared.gpx.TrackItem;
import net.osmand.shared.io.KFile;
import net.osmand.shared.util.KMapUtils;
import net.osmand.util.CollectionUtils;

import java.util.Comparator;

public class TracksComparator implements Comparator<Object> {

	public final KLatLon latLon;
	public final TrackTab trackTab;
	public final TracksSortMode sortMode;
	public final Collator collator = OsmAndCollator.primaryCollator();
	private boolean useSubdirs = false;

	public TracksComparator(@NonNull TrackTab trackTab, @NonNull LatLon latLon) {
		this.trackTab = trackTab;
		this.sortMode = trackTab.getSortMode();
		this.latLon = SharedUtil.kLatLon(latLon);
	}

	public TracksComparator(@NonNull TracksSortMode sortMode,
	                        @NonNull LatLon latLon, boolean useSubdirs) {
		this(sortMode, latLon);
		this.useSubdirs = useSubdirs;
	}

	public TracksComparator(@NonNull TracksSortMode sortMode, @NonNull LatLon latLon) {
		this.trackTab = null;
		this.sortMode = sortMode;
		this.latLon = SharedUtil.kLatLon(latLon);
	}

	@Override
	public int compare(Object o1, Object o2) {
		if (o1 instanceof Integer) {
			return o2 instanceof Integer ? Integer.compare((Integer) o1, (Integer) o2) : -1;
		}
		if (o2 instanceof Integer) {
			return 1;
		}
		if (o1 instanceof TrackItem && ((TrackItem) o1).isShowCurrentTrack()) {
			return -1;
		}
		if (o2 instanceof TrackItem && ((TrackItem) o2).isShowCurrentTrack()) {
			return 1;
		}
		if (o1 instanceof VisibleTracksGroup) {
			return -1;
		}
		if (o2 instanceof VisibleTracksGroup) {
			return 1;
		}
		if (o1 instanceof ComparableTracksGroup folder1) {
			if (o2 instanceof ComparableTracksGroup folder2) {
				int predefinedOrder1 = folder1.getDefaultOrder();
				int predefinedOrder2 = folder2.getDefaultOrder();
				if (predefinedOrder1 != predefinedOrder2) {
					return Integer.compare(predefinedOrder1, predefinedOrder2);
				}
				return compareTrackFolders(folder1, folder2);
			}
			return -1;
		}
		if (o2 instanceof ComparableTracksGroup) {
			return 1;
		}
		if (o1 instanceof TrackItem && o2 instanceof TrackItem) {
			return compareTrackItems((TrackItem) o1, (TrackItem) o2);
		}
		return 0;
	}

	private int compareTrackFolders(@NonNull ComparableTracksGroup folder1, @NonNull ComparableTracksGroup folder2) {
		int multiplier;
		switch (sortMode) {
			case NAME_ASCENDING, NAME_DESCENDING: {
				multiplier = sortMode == NAME_ASCENDING ? 1 : -1;
				return multiplier * compareTrackFolderNames(folder1, folder2);
			}

			case LAST_MODIFIED, DATE_ASCENDING, DATE_DESCENDING: {
				multiplier = sortMode == DATE_DESCENDING ? -1 : 1;
				return multiplier * compareFolderFilesByLastModified(folder1, folder2);
			}

			case DISTANCE_ASCENDING, DISTANCE_DESCENDING: {
				float dist1 = folder1.getFolderAnalysis().getTotalDistance();
				float dist2 = folder2.getFolderAnalysis().getTotalDistance();
				if (Math.abs(dist1 - dist2) >= EQUIVALENT_TOLERANCE) {
					multiplier = sortMode == DISTANCE_ASCENDING ? 1 : -1;
					return multiplier * Float.compare(dist1, dist2);
				}
			}

			case DURATION_ASCENDING, DURATION_DESCENDING: {
				int timeSpan1 = folder1.getFolderAnalysis().getTimeSpan();
				int timeSpan2 = folder2.getFolderAnalysis().getTimeSpan();
				if (timeSpan1 != timeSpan2) {
					multiplier = sortMode == DURATION_ASCENDING ? 1 : -1;
					return multiplier * Long.compare(timeSpan1, timeSpan2);
				}
			}
		}
		return compareTrackFolderNames(folder1, folder2);
	}

	private int compareTrackItems(@NonNull TrackItem item1, @NonNull TrackItem item2) {
		Integer currentTrack = checkCurrentTrack(item1, item2);
		if (currentTrack != null) {
			return currentTrack;
		}

		GpxDataItem dataItem1 = item1.getDataItem();
		GpxDataItem dataItem2 = item2.getDataItem();
		GpxTrackAnalysis analysis1;
		GpxTrackAnalysis analysis2;

		if (shouldCheckAnalysis()) {
			analysis1 = dataItem1 != null ? dataItem1.getAnalysis() : null;
			analysis2 = dataItem2 != null ? dataItem2.getAnalysis() : null;
			Integer analysis = checkItemsAnalysis(item1, item2, analysis1, analysis2);
			if (analysis != null) {
				return analysis;
			}
		}

		switch (sortMode) {
			case NEAREST:
				analysis1 = dataItem1 != null ? dataItem1.getAnalysis() : null;
				analysis2 = dataItem2 != null ? dataItem2.getAnalysis() : null;
				return compareNearestItems(item1, item2, analysis1, analysis2);
			case NAME_ASCENDING:
				return compareTrackItemNames(item1, item2);
			case NAME_DESCENDING:
				return -compareTrackItemNames(item1, item2);
			case DATE_ASCENDING:
				analysis1 = dataItem1 != null ? dataItem1.getAnalysis() : null;
				analysis2 = dataItem2 != null ? dataItem2.getAnalysis() : null;
				long startTime1_asc = analysis1 == null ? 0 : analysis1.getStartTime();
				long startTime2_asc = analysis2 == null ? 0 : analysis2.getStartTime();
				long time1_asc = dataItem1 == null ? startTime1_asc : (long) dataItem1.getParameter(FILE_CREATION_TIME);
				long time2_asc = dataItem2 == null ? startTime2_asc : (long) dataItem2.getParameter(FILE_CREATION_TIME);
				if (time1_asc == time2_asc || time1_asc < 10 && time2_asc < 10) {
					return compareTrackItemNames(item1, item2);
				}
				if (time1_asc < 10) {
					return 1;
				} else if (time2_asc < 10) {
					return -1;
				}
				return -Long.compare(time1_asc, time2_asc);
			case DATE_DESCENDING:
				analysis1 = dataItem1 != null ? dataItem1.getAnalysis() : null;
				analysis2 = dataItem2 != null ? dataItem2.getAnalysis() : null;
				long startTime1_desc = analysis1 == null ? 0 : analysis1.getStartTime();
				long startTime2_desc = analysis2 == null ? 0 : analysis2.getStartTime();
				long time1_desc = dataItem1 == null ? startTime1_desc : (long) dataItem1.getParameter(FILE_CREATION_TIME);
				long time2_desc = dataItem2 == null ? startTime2_desc : (long) dataItem2.getParameter(FILE_CREATION_TIME);
				if (time1_desc == time2_desc || time1_desc < 10 && time2_desc < 10) {
					return compareTrackItemNames(item1, item2);
				}
				if (time1_desc < 10) {
					return 1;
				} else if (time2_desc < 10) {
					return -1;
				}
				return Long.compare(time1_desc, time2_desc);
			case LAST_MODIFIED:
				return compareItemFilesByLastModified(item1, item2);
			case DISTANCE_DESCENDING:
				analysis1 = dataItem1 != null ? dataItem1.getAnalysis() : null;
				analysis2 = dataItem2 != null ? dataItem2.getAnalysis() : null;
				if (Math.abs(analysis1.getTotalDistance() - analysis2.getTotalDistance()) < EQUIVALENT_TOLERANCE) {
					return compareTrackItemNames(item1, item2);
				}
				return -Float.compare(analysis1.getTotalDistance(), analysis2.getTotalDistance());
			case DISTANCE_ASCENDING:
				analysis1 = dataItem1 != null ? dataItem1.getAnalysis() : null;
				analysis2 = dataItem2 != null ? dataItem2.getAnalysis() : null;
				if (Math.abs(analysis1.getTotalDistance() - analysis2.getTotalDistance()) < EQUIVALENT_TOLERANCE) {
					return compareTrackItemNames(item1, item2);
				}
				return Float.compare(analysis1.getTotalDistance(), analysis2.getTotalDistance());
			case DURATION_DESCENDING:
				analysis1 = dataItem1 != null ? dataItem1.getAnalysis() : null;
				analysis2 = dataItem2 != null ? dataItem2.getAnalysis() : null;
				if (analysis1.getDurationInSeconds() == analysis2.getDurationInSeconds()) {
					return compareTrackItemNames(item1, item2);
				}
				return -Long.compare(analysis1.getDurationInSeconds(), analysis2.getDurationInSeconds());
			case DURATION_ASCENDING:
				analysis1 = dataItem1 != null ? dataItem1.getAnalysis() : null;
				analysis2 = dataItem2 != null ? dataItem2.getAnalysis() : null;
				if (analysis1.getDurationInSeconds() == analysis2.getDurationInSeconds()) {
					return compareTrackItemNames(item1, item2);
				}
				return Long.compare(analysis1.getDurationInSeconds(), analysis2.getDurationInSeconds());
		}
		return 0;
	}

	private boolean shouldCheckAnalysis() {
		return !CollectionUtils.equalsToAny(sortMode, NAME_ASCENDING, NAME_DESCENDING, LAST_MODIFIED);
	}

	@Nullable
	private Integer checkCurrentTrack(@NonNull TrackItem item1, @NonNull TrackItem item2) {
		if (item1.isShowCurrentTrack()) {
			return -1;
		}
		if (item2.isShowCurrentTrack()) {
			return 1;
		}
		return null;
	}

	@Nullable
	private Integer checkItemsAnalysis(@NonNull TrackItem item1, @NonNull TrackItem item2,
	                                   @Nullable GpxTrackAnalysis analysis1, @Nullable GpxTrackAnalysis analysis2) {
		if (analysis1 == null) {
			return analysis2 == null ? compareTrackItemNames(item1, item2) : 1;
		}
		if (analysis2 == null) {
			return -1;
		}
		return null;
	}

	private int compareNearestItems(@NonNull TrackItem item1, @NonNull TrackItem item2,
	                                @NonNull GpxTrackAnalysis analysis1, @NonNull GpxTrackAnalysis analysis2) {
		if (analysis1.getLatLonStart() == null) {
			return analysis2.getLatLonStart() == null ? compareTrackItemNames(item1, item2) : 1;
		}
		if (analysis2.getLatLonStart() == null) {
			return -1;
		}
		if (analysis1.getLatLonStart().equals(analysis2.getLatLonStart())) {
			return compareTrackItemNames(item1, item2);
		}
		double distance1 = KMapUtils.INSTANCE.getDistance(latLon, analysis1.getLatLonStart());
		double distance2 = KMapUtils.INSTANCE.getDistance(latLon, analysis2.getLatLonStart());
		return Double.compare(distance1, distance2);
	}

	private int compareItemFilesByLastModified(@NonNull TrackItem item1, @NonNull TrackItem item2) {
		KFile file1 = item1.getFile();
		KFile file2 = item2.getFile();

		if (file1 == null) {
			return file2 == null ? compareTrackItemNames(item1, item2) : 1;
		}
		if (file2 == null) {
			return -1;
		}
		if (file1.lastModified() == file2.lastModified()) {
			return compareTrackItemNames(item1, item2);
		}
		return compareFilesByLastModified(file1.lastModified(), file2.lastModified());
	}

	private int compareFolderFilesByLastModified(@NonNull ComparableTracksGroup folder1, @NonNull ComparableTracksGroup folder2) {
		long lastModified1 = folder1.lastModified();
		long lastModified2 = folder2.lastModified();

		if (lastModified1 == lastModified2) {
			return compareTrackFolderNames(folder1, folder2);
		}
		return compareFilesByLastModified(lastModified1, lastModified2);
	}

	private int compareFilesByLastModified(long lastModified1, long lastModified2) {
		return -Long.compare(lastModified1, lastModified2);
	}

	private int compareTrackItemNames(@NonNull TrackItem item1, @NonNull TrackItem item2) {
		return compareNames(item1.getName(), item2.getName());
	}

	private int compareTrackFolderNames(@NonNull ComparableTracksGroup folder1,
	                                    @NonNull ComparableTracksGroup folder2) {
		return compareNames(folder1.getDirName(useSubdirs), folder2.getDirName(useSubdirs));
	}

	private int compareNames(@NonNull String item1, @NonNull String item2) {
		return collator.compare(item1, item2);
	}
}
