package net.osmand.plus.plugins.monitoring.actions;

import static net.osmand.plus.quickaction.QuickActionIds.START_NEW_TRIP_SEGMENT_ACTION;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.plugins.monitoring.OsmandMonitoringPlugin;
import net.osmand.plus.quickaction.QuickAction;
import net.osmand.plus.quickaction.QuickActionType;

public class StartNewTripSegmentAction extends BaseMonitoringAction {

	public static final QuickActionType TYPE = new QuickActionType(START_NEW_TRIP_SEGMENT_ACTION,
			"start.new.trip.segment", StartNewTripSegmentAction.class)
			.nameRes(R.string.new_trip_segment)
			.iconRes(R.drawable.ic_action_trip_rec_new_segment)
			.nonEditable()
			.category(QuickActionType.MY_PLACES)
			.nameActionRes(R.string.shared_string_control_start);

	public StartNewTripSegmentAction() {
		super(TYPE);
	}

	public StartNewTripSegmentAction(QuickAction quickAction) {
		super(quickAction);
	}

	@Override
	public void execute(@NonNull MapActivity mapActivity, @Nullable Bundle params) {
		OsmandMonitoringPlugin plugin = getPlugin();
		if (plugin != null) {
			OsmandApplication app = mapActivity.getMyApplication();
			if (isRecordingTrack()) {
				app.getSavingTrackHelper().startNewSegment();
				app.showToastMessage(R.string.new_segment_started_m);
			} else {
				app.showToastMessage(R.string.start_trip_recording_first_m);
			}
		}
	}

	@Override
	public void drawUI(@NonNull ViewGroup parent, @NonNull MapActivity mapActivity) {
		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.quick_action_with_text, parent, false);
		((TextView) view.findViewById(R.id.text)).setText(R.string.quick_action_new_trip_segment);
		parent.addView(view);
	}
}
