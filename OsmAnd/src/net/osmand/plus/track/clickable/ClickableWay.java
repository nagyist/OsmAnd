package net.osmand.plus.track.clickable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.data.LatLon;
import net.osmand.plus.mapcontextmenu.controllers.SelectedGpxMenuController.SelectedGpxPoint;
import net.osmand.shared.gpx.GpxFile;
import net.osmand.shared.gpx.primitives.WptPt;
import net.osmand.util.Algorithms;

public class ClickableWay {
    private final long osmId;
    private final String name;
    private final GpxFile gpxFile;
    private final SelectedGpxPoint selectedGpxPoint;

    public ClickableWay(@NonNull GpxFile gpxFile, long osmId, @Nullable String name,
                        @NonNull LatLon selectedPointCoordinates) {
        this.gpxFile = gpxFile;
        this.osmId = osmId;
        this.name = name;
        WptPt wpt = new WptPt();
        wpt.setLat(selectedPointCoordinates.getLatitude());
        wpt.setLon(selectedPointCoordinates.getLongitude());
        this.selectedGpxPoint = new SelectedGpxPoint(null, wpt);
    }

    public long getOsmId() {
        return osmId;
    }

    public GpxFile getGpxFile() {
        return gpxFile;
    }

    public SelectedGpxPoint getSelectedGpxPoint() {
        return selectedGpxPoint;
    }

    public String getGpxFileName() {
        return Algorithms.sanitizeFileName(getWayName());
    }

    public String getWayName() {
        return Algorithms.isEmpty(name) ? Long.toString(osmId) : name;
    }

    public String toString() {
        return getWayName();
    }
}
