package net.osmand.plus.settings.bottomsheets;

import static net.osmand.plus.utils.OsmAndFormatter.METERS_IN_KILOMETER;
import static net.osmand.plus.utils.OsmAndFormatter.getFormattedSpeed;
import static net.osmand.plus.utils.OsmAndFormatter.getFormattedSpeedValue;
import static net.osmand.plus.utils.OsmAndFormatter.getMetersInModeUnit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;

import com.google.android.material.slider.Slider;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem;
import net.osmand.plus.base.bottomsheetmenu.simpleitems.DividerSpaceItem;
import net.osmand.plus.base.bottomsheetmenu.simpleitems.LongDescriptionItem;
import net.osmand.plus.base.bottomsheetmenu.simpleitems.TitleItem;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.fragments.OnPreferenceChanged;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.UiUtilities;

public class SpeedLimitBottomSheet extends BasePreferenceBottomSheet {

	private static final String TAG = SpeedLimitBottomSheet.class.getSimpleName();

	private static final String SELECTED_VALUE = "selected_value";
	private static final float MIN_VALUE_KM_H = -10;
	private static final float MAX_VALUE_KM_H = 20;

	private OsmandApplication app;
	private float selectedValue;

	@Override
	public void createMenuItems(Bundle savedInstanceState) {
		app = requiredMyApplication();

		if (savedInstanceState != null) {
			selectedValue = savedInstanceState.getFloat(SELECTED_VALUE);
		} else {
			float value = app.getSettings().SPEED_LIMIT_EXCEED_KMH.getModeValue(getAppMode());
			selectedValue = value;
		}

		int padding = getResources().getDimensionPixelSize(R.dimen.content_padding);

		items.add(new TitleItem(getString(R.string.speed_limit_exceed)));
		items.add(new LongDescriptionItem(getString(R.string.speed_limit_exceed_message)));
		items.add(new DividerSpaceItem(app, padding));
		items.add(createSliderItem());
		items.add(new DividerSpaceItem(app, padding));
	}

	@NonNull
	private BaseBottomSheetItem createSliderItem() {
		ApplicationMode appMode = getAppMode();
		LayoutInflater inflater = UiUtilities.getInflater(getContext(), nightMode);
		View view = inflater.inflate(R.layout.bottom_sheet_item_slider_with_four_text, null);

		TextView title = view.findViewById(R.id.title);
		title.setText(R.string.selected_value);

		TextView summary = view.findViewById(R.id.summary);
		summary.setText(getFormattedSpeed(selectedValue / 3.6f, app, appMode, true));

		TextView fromTv = view.findViewById(R.id.from_value);
		fromTv.setText(getFormattedSpeed(MIN_VALUE_KM_H / 3.6f, app, appMode, true));

		TextView toTv = view.findViewById(R.id.to_value);
		toTv.setText(getFormattedSpeed(MAX_VALUE_KM_H / 3.6f, app, appMode, true));

		Slider slider = view.findViewById(R.id.slider);
		slider.setValue(Float.parseFloat(getFormattedSpeedValue(selectedValue / 3.6f, app, appMode, true).value));
		slider.setValueFrom(Float.parseFloat(getFormattedSpeedValue(MIN_VALUE_KM_H / 3.6f, app, appMode, true).value));
		slider.setValueTo(Float.parseFloat(getFormattedSpeedValue(MAX_VALUE_KM_H / 3.6f, app, appMode, true).value));
		slider.setStepSize(1f);
		slider.addOnChangeListener((s, value, fromUser) -> {
			selectedValue = (value * getMetersInModeUnit(app, appMode) / METERS_IN_KILOMETER);
			summary.setText(getFormattedSpeed(selectedValue / 3.6f, app, appMode, true));
		});

		int color = appMode.getProfileColor(nightMode);
		UiUtilities.setupSlider(slider, nightMode, color, false);

		return new BaseBottomSheetItem.Builder()
				.setCustomView(view)
				.create();
	}

	@Override
	protected void onRightBottomButtonClick() {
		Preference preference = getPreference();
		if (preference != null) {
			float value = selectedValue;
			if (preference.callChangeListener(value)) {
				app.getSettings().SPEED_LIMIT_EXCEED_KMH.setModeValue(getAppMode(), value);
			}
			Fragment target = getTargetFragment();
			if (target instanceof OnPreferenceChanged) {
				((OnPreferenceChanged) target).onPreferenceChanged(preference.getKey());
			}
		}
		dismiss();
	}

	@Override
	protected int getRightBottomButtonTextId() {
		return R.string.shared_string_apply;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putFloat(SELECTED_VALUE, selectedValue);
	}

	public static void showInstance(@NonNull FragmentManager manager, @NonNull Fragment target,
	                                @NonNull String key, @NonNull ApplicationMode appMode) {
		if (AndroidUtils.isFragmentCanBeAdded(manager, TAG)) {
			Bundle args = new Bundle();
			args.putString(PREFERENCE_ID, key);

			SpeedLimitBottomSheet fragment = new SpeedLimitBottomSheet();
			fragment.setArguments(args);
			fragment.setAppMode(appMode);
			fragment.setTargetFragment(target, 0);
			fragment.show(manager, TAG);
		}
	}
}