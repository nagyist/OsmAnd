package net.osmand.plus.settings.backend.preferences;

import androidx.annotation.NonNull;

import net.osmand.plus.settings.backend.OsmandSettings;

public class BooleanPreference extends CommonPreference<Boolean> {

	public BooleanPreference(@NonNull OsmandSettings settings, @NonNull String id,
			boolean defaultValue) {
		super(settings, id, defaultValue);
	}

	@Override
	public Boolean getValue(@NonNull Object prefs, Boolean defaultValue) {
		return getSettingsAPI().getBoolean(prefs, getId(), defaultValue);
	}

	@Override
	protected boolean setValue(Object prefs, Boolean val) {
		return super.setValue(prefs, val)
				&& getSettingsAPI().edit(prefs).putBoolean(getId(), val).commit();
	}

	@Override
	public Boolean parseString(String s) {
		return Boolean.parseBoolean(s);
	}
}