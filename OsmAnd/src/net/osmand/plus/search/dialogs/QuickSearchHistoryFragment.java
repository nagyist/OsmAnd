package net.osmand.plus.search.dialogs;

import android.app.Dialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.data.LatLon;
import net.osmand.plus.OsmAndLocationProvider;
import net.osmand.plus.OsmAndLocationProvider.OsmAndCompassListener;
import net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.BaseFullScreenDialogFragment;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.search.QuickSearchHelper.SearchHistoryAPI;
import net.osmand.plus.search.history.HistoryEntry;
import net.osmand.plus.search.listitems.QuickSearchListItem;
import net.osmand.plus.settings.enums.HistorySource;
import net.osmand.plus.settings.fragments.BaseSettingsFragment;
import net.osmand.plus.settings.fragments.SettingsScreenType;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.utils.InsetTarget;
import net.osmand.plus.utils.InsetTargetsCollection;
import net.osmand.plus.widgets.tools.SimpleTextWatcher;
import net.osmand.search.core.SearchPhrase;
import net.osmand.search.core.SearchSettings;
import net.osmand.search.core.SearchResult;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuickSearchHistoryFragment extends BaseFullScreenDialogFragment implements OsmAndCompassListener, OsmAndLocationListener {

	public static final String TAG = QuickSearchHistoryFragment.class.getSimpleName();
	private static final org.apache.commons.logging.Log LOG = PlatformUtil.getLog(QuickSearchHistoryFragment.class);

	private enum HistorySortMode {
		RECENT(R.string.sort_by_last_viewed, R.drawable.ic_action_sort),
		NEAREST(R.string.shared_string_nearest, R.drawable.ic_action_sort),
		MAP_CENTER(R.string.sort_by_nearest_to_map_center, R.drawable.ic_action_sort);

		final int titleId;
		final int iconId;

		HistorySortMode(int titleId, int iconId) {
			this.titleId = titleId;
			this.iconId = iconId;
		}
	}

	private enum HistorySourceFilter {
		ALL(R.string.shared_string_all, R.drawable.ic_action_history, null),
		SEARCH(R.string.shared_string_search, R.drawable.ic_action_search_dark, HistorySource.SEARCH),
		NAVIGATION(R.string.shared_string_navigation, R.drawable.ic_action_gdirections_dark, HistorySource.NAVIGATION);

		final int titleId;
		final int iconId;
		@Nullable
		final HistorySource source;

		HistorySourceFilter(int titleId, int iconId, @Nullable HistorySource source) {
			this.titleId = titleId;
			this.iconId = iconId;
			this.source = source;
		}
	}

	private QuickSearchHistoryAdapter adapter;
	private TextView titleView;
	private AppCompatEditText searchEditText;
	private ImageButton settingsButton;
	private ImageButton clearButton;
	private QuickSearchChipsToolbarView chipsToolbar;

	private Float heading;
	private Location location;
	private boolean locationUpdateStarted;
	private boolean compassUpdateAllowed = true;
	private boolean touching;
	private boolean searchFieldActive;
	private HistorySortMode selectedSortMode = HistorySortMode.RECENT;
	private HistorySourceFilter selectedSourceFilter = HistorySourceFilter.ALL;
	private final List<String> selectedTypeFilters = new ArrayList<>();

	@ColorRes
	@Override
	protected int getStatusBarColorId() {
		return ColorUtilities.getListBgColorId(nightMode);
	}

	@Override
	public void onStart() {
		super.onStart();
		updateStatusBarAppearance();
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable android.os.Bundle savedInstanceState) {
		updateNightMode();
		View view = inflate(R.layout.quick_search_history_fragment, container, false);

		setupToolbar(view);
		setupChips(view);
		setupList(view);
		updateHistoryItems("");
		updateStatusBarAppearance();

		return view;
	}

	private void updateStatusBarAppearance() {
		Dialog dialog = getDialog();
		Window window = dialog != null ? dialog.getWindow() : null;
		if (window != null) {
			AndroidUiHelper.setStatusBarColor(window, getColor(getStatusBarColorId()));
			AndroidUiHelper.setStatusBarContentColor(window.getDecorView(), nightMode);
		}
	}

	private void setupToolbar(@NonNull View view) {
		Toolbar toolbar = view.findViewById(R.id.toolbar);
		int iconColor = ColorUtilities.getDefaultIconColor(app, nightMode);
		toolbar.setNavigationIcon(getPaintedIcon(AndroidUtils.getNavigationIconResId(app), iconColor));
		toolbar.setNavigationContentDescription(R.string.shared_string_back);
		toolbar.setNavigationOnClickListener(v -> dismiss());

		titleView = view.findViewById(R.id.title);
		titleView.setOnClickListener(v -> activateSearchField());

		settingsButton = view.findViewById(R.id.settings_button);
		settingsButton.setImageDrawable(getPaintedIcon(R.drawable.ic_action_settings_outlined, iconColor));
		settingsButton.setOnClickListener(v -> openHistorySettings());

		clearButton = view.findViewById(R.id.clear_button);
		clearButton.setImageDrawable(getPaintedIcon(R.drawable.ic_action_remove_dark, iconColor));
		clearButton.setOnClickListener(v -> searchEditText.setText(""));

		searchEditText = view.findViewById(R.id.search);
		searchEditText.addTextChangedListener(new SimpleTextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				String query = s == null ? "" : s.toString();
				updateToolbarActions(query);
				updateHistoryItems(query);
			}
		});
		updateToolbarActions("");
	}

	private void setupChips(@NonNull View view) {
		chipsToolbar = view.findViewById(R.id.chips_toolbar);
		chipsToolbar.setOnSortSelectedListener(optionId -> {
			HistorySortMode[] modes = HistorySortMode.values();
			if (optionId >= 0 && optionId < modes.length) {
				selectedSortMode = modes[optionId];
				updateSortChip();
				updateHistoryItems(searchEditText.getText().toString());
			}
		});
		chipsToolbar.setOnSourceSelectedListener(optionId -> {
			if (isSourceMenuDisabled()) {
				return;
			}
			HistorySourceFilter[] filters = HistorySourceFilter.values();
			if (optionId >= 0 && optionId < filters.length) {
				selectedSourceFilter = filters[optionId];
				selectedTypeFilters.clear();
				updateSourceChip();
				updateHistoryItems(searchEditText.getText().toString());
			}
		});
		chipsToolbar.setOnTypeChipClickListener(typeName -> {
			if (selectedTypeFilters.remove(typeName)) {
				updateHistoryItems(searchEditText.getText().toString());
			} else {
				selectedTypeFilters.add(typeName);
				updateHistoryItems(searchEditText.getText().toString());
			}
		});
		updateSourceFilterFromSettings();
		updateSortChip();
		updateSourceChip();
	}

	private void activateSearchField() {
		if (searchFieldActive) {
			return;
		}
		searchFieldActive = true;
		titleView.setVisibility(View.GONE);
		searchEditText.setVisibility(View.VISIBLE);
		searchEditText.requestFocus();
		searchEditText.post(() -> {
			if (isAdded()) {
				AndroidUtils.showSoftKeyboard(requireActivity(), searchEditText);
			}
		});
	}

	private void setupList(@NonNull View view) {
		MapActivity mapActivity = (MapActivity) requireActivity();
		ListView listView = view.findViewById(R.id.list);
		listView.setBackgroundColor(ColorUtilities.getActivityBgColor(app, nightMode));
		listView.setOnTouchListener((v, event) -> {
			switch (event.getAction()) {
				case android.view.MotionEvent.ACTION_DOWN:
				case android.view.MotionEvent.ACTION_POINTER_DOWN:
					touching = true;
					break;
				case android.view.MotionEvent.ACTION_UP:
				case android.view.MotionEvent.ACTION_POINTER_UP:
				case android.view.MotionEvent.ACTION_CANCEL:
					touching = false;
					break;
			}
			return false;
		});
		listView.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
				compassUpdateAllowed = scrollState == SCROLL_STATE_IDLE;
				if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
					AndroidUtils.hideSoftKeyboard(requireActivity(), searchEditText);
				}
			}

			@Override
			public void onScroll(android.widget.AbsListView view, int firstVisibleItem, int visibleItemCount,
					int totalItemCount) {
			}
		});
		listView.setOnItemClickListener((parent, itemView, position, id) -> {
			QuickSearchHistoryAdapter.Item item = adapter.getItem(position);
			if (item != null && item.getListItem() != null) {
				onHistoryItemClick(item.getListItem());
			}
		});

		adapter = new QuickSearchHistoryAdapter(app, mapActivity, nightMode);
		listView.setAdapter(adapter);
	}

	private void onHistoryItemClick(@NonNull QuickSearchListItem item) {
		Fragment target = getTargetFragment();
		SearchResult searchResult = item.getSearchResult();
		if (target instanceof QuickSearchDialogFragment quickSearchDialogFragment && searchResult != null) {
			dismissAllowingStateLoss();
			quickSearchDialogFragment.showSearchHistoryResult(searchResult);
		}
	}

	private void updateToolbarActions(@NonNull String query) {
		boolean searchActive = !TextUtils.isEmpty(query);
		settingsButton.setVisibility(searchActive ? View.GONE : View.VISIBLE);
		clearButton.setVisibility(searchActive ? View.VISIBLE : View.GONE);
	}

	private void updateHistoryItems(@NonNull String query) {
		try {
			List<HistoryRecord> records = loadHistoryRecords(query);
			updateTypeFilterChips(records);
			records = applyTypeFilters(records);
			sortRecords(records);
			if (adapter != null) {
				adapter.setUseMapCenter(selectedSortMode == HistorySortMode.MAP_CENTER);
				adapter.setItems(createAdapterItems(records));
			}
		} catch (Exception e) {
			LOG.error(e);
			app.showToastMessage(e.getMessage());
		}
	}

	@NonNull
	private List<HistoryRecord> loadHistoryRecords(@NonNull String query) {
		List<HistoryRecord> records = new ArrayList<>();
		String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
		SearchPhrase phrase = createHistoryPhrase();
		for (HistoryEntry entry : app.getSearchHistoryHelper().getVisibleHistoryEntries(selectedSourceFilter.source, false, true)) {
			SearchResult result = SearchHistoryAPI.createSearchResult(app, entry, phrase);
			QuickSearchListItem item = new QuickSearchListItem(app, result);
			if (Algorithms.isEmpty(normalizedQuery) || matchesQuery(item, normalizedQuery)) {
				records.add(new HistoryRecord(entry, item));
			}
		}
		return records;
	}

	@NonNull
	private SearchPhrase createHistoryPhrase() {
		SearchSettings settings = app.getSearchUICore().getCore().getSearchSettings();
		LatLon origin = selectedSortMode == HistorySortMode.MAP_CENTER ? getMapCenter() : null;
		if (origin != null) {
			settings = settings.setOriginalLocation(origin);
		}
		return SearchPhrase.emptyPhrase(settings);
	}

	private boolean matchesQuery(@NonNull QuickSearchListItem item, @NonNull String query) {
		return containsIgnoreCase(item.getName(), query)
				|| containsIgnoreCase(item.getTypeName(), query)
				|| containsIgnoreCase(item.getAddress(), query);
	}

	private boolean containsIgnoreCase(@Nullable String value, @NonNull String query) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(query);
	}

	@NonNull
	private List<HistoryRecord> applyTypeFilters(@NonNull List<HistoryRecord> records) {
		if (selectedTypeFilters.isEmpty()) {
			return records;
		}
		List<HistoryRecord> filtered = new ArrayList<>();
		for (HistoryRecord record : records) {
			if (selectedTypeFilters.contains(record.typeName)) {
				filtered.add(record);
			}
		}
		return filtered;
	}

	private void sortRecords(@NonNull List<HistoryRecord> records) {
		if (selectedSortMode == HistorySortMode.RECENT) {
			Collections.sort(records, (o1, o2) -> Long.compare(o2.time, o1.time));
			return;
		}
		LatLon origin = selectedSortMode == HistorySortMode.MAP_CENTER ? getMapCenter() : getMyLocation();
		Collections.sort(records, Comparator.comparingDouble(record -> getDistance(record, origin)));
	}

	private double getDistance(@NonNull HistoryRecord record, @Nullable LatLon origin) {
		SearchResult result = record.item.getSearchResult();
		if (origin == null || result == null || result.location == null) {
			return Double.MAX_VALUE;
		}
		return MapUtils.getDistance(origin, result.location);
	}

	@Nullable
	private LatLon getMyLocation() {
		Location current = location != null ? location : app.getLocationProvider().getLastKnownLocation();
		return current != null ? new LatLon(current.getLatitude(), current.getLongitude()) : null;
	}

	@Nullable
	private LatLon getMapCenter() {
		MapActivity mapActivity = getMapActivity();
		return mapActivity != null ? mapActivity.getMapLocation() : null;
	}

	@NonNull
	private List<QuickSearchHistoryAdapter.Item> createAdapterItems(@NonNull List<HistoryRecord> records) {
		List<QuickSearchHistoryAdapter.Item> items = new ArrayList<>();
		if (selectedSortMode != HistorySortMode.RECENT) {
			for (HistoryRecord record : records) {
				items.add(QuickSearchHistoryAdapter.result(record.item));
			}
			return items;
		}
		String previousHeader = null;
		for (HistoryRecord record : records) {
			String header = getHeader(record.time);
			if (!Algorithms.objectEquals(previousHeader, header)) {
				items.add(QuickSearchHistoryAdapter.header(header));
				previousHeader = header;
			}
			items.add(QuickSearchHistoryAdapter.result(record.item));
		}
		return items;
	}

	@NonNull
	private String getHeader(long time) {
		Calendar now = Calendar.getInstance();
		Calendar item = Calendar.getInstance();
		item.setTimeInMillis(time);
		if (isSameDay(now, item)) {
			return getString(R.string.today);
		}
		Calendar todayStart = startOfDay(now);
		Calendar yesterdayStart = (Calendar) todayStart.clone();
		yesterdayStart.add(Calendar.DAY_OF_YEAR, -1);
		Calendar itemStart = startOfDay(item);
		if (!itemStart.before(yesterdayStart) && itemStart.before(todayStart)) {
			return getString(R.string.yesterday);
		}
		Calendar lastWeekStart = (Calendar) todayStart.clone();
		lastWeekStart.add(Calendar.DAY_OF_YEAR, -7);
		if (!itemStart.before(lastWeekStart) && itemStart.before(yesterdayStart)) {
			return getString(R.string.last_week);
		}
		return new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(item.getTime());
	}

	@NonNull
	private Calendar startOfDay(@NonNull Calendar calendar) {
		Calendar result = (Calendar) calendar.clone();
		result.set(Calendar.HOUR_OF_DAY, 0);
		result.set(Calendar.MINUTE, 0);
		result.set(Calendar.SECOND, 0);
		result.set(Calendar.MILLISECOND, 0);
		return result;
	}

	private boolean isSameDay(@NonNull Calendar first, @NonNull Calendar second) {
		return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
				&& first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
	}

	private void updateTypeFilterChips(@NonNull List<HistoryRecord> records) {
		if (chipsToolbar == null) {
			return;
		}
		Map<String, String> typeNames = new LinkedHashMap<>();
		for (HistoryRecord record : records) {
			if (!Algorithms.isEmpty(record.typeName)) {
				typeNames.put(record.typeName, record.typeName);
			}
		}
		selectedTypeFilters.retainAll(typeNames.keySet());
		List<String> chips = new ArrayList<>();
		for (String selected : new ArrayList<>(selectedTypeFilters)) {
			String typeName = typeNames.remove(selected);
			if (typeName != null) {
				chips.add(typeName);
			}
		}
		for (String typeName : typeNames.values()) {
			chips.add(typeName);
		}
		chipsToolbar.setTypeChips(chips, new ArrayList<>(selectedTypeFilters));
	}

	private void updateSortChip() {
		if (chipsToolbar != null) {
			chipsToolbar.setSortChip(selectedSortMode.titleId, selectedSortMode.iconId);
			List<QuickSearchChipsToolbarView.Option> options = new ArrayList<>();
			for (HistorySortMode sortMode : HistorySortMode.values()) {
				options.add(new QuickSearchChipsToolbarView.Option(
						sortMode.ordinal(), sortMode.titleId, selectedSortMode == sortMode));
			}
			chipsToolbar.setSortOptions(options);
		}
	}

	private void updateSourceChip() {
		if (chipsToolbar != null) {
			updateSourceFilterFromSettings();
			chipsToolbar.setSourceChip(selectedSourceFilter.titleId, selectedSourceFilter.iconId);
			chipsToolbar.setSourceMenuEnabled(!isSourceMenuDisabled());
			List<QuickSearchChipsToolbarView.Option> options = new ArrayList<>();
			for (HistorySourceFilter sourceFilter : HistorySourceFilter.values()) {
				options.add(new QuickSearchChipsToolbarView.Option(
						sourceFilter.ordinal(), sourceFilter.titleId, selectedSourceFilter == sourceFilter));
			}
			chipsToolbar.setSourceOptions(options);
		}
	}

	private void updateSourceFilterFromSettings() {
		HistorySourceFilter previousSourceFilter = selectedSourceFilter;
		boolean searchHistoryEnabled = settings.SEARCH_HISTORY.get();
		boolean navigationHistoryEnabled = settings.NAVIGATION_HISTORY.get();
		if (!searchHistoryEnabled && navigationHistoryEnabled) {
			selectedSourceFilter = HistorySourceFilter.NAVIGATION;
		} else if (searchHistoryEnabled && !navigationHistoryEnabled) {
			selectedSourceFilter = HistorySourceFilter.SEARCH;
		} else if (!searchHistoryEnabled) {
			selectedSourceFilter = HistorySourceFilter.ALL;
		}
		if (previousSourceFilter != selectedSourceFilter) {
			selectedTypeFilters.clear();
		}
	}

	private boolean isSourceMenuDisabled() {
		return !settings.SEARCH_HISTORY.get() || !settings.NAVIGATION_HISTORY.get();
	}

	private static class HistoryRecord {
		final long time;
		final QuickSearchListItem item;
		final String typeName;

		HistoryRecord(@NonNull HistoryEntry entry, @NonNull QuickSearchListItem item) {
			this.time = entry.getLastAccessTime();
			this.item = item;
			this.typeName = item.getTypeName();
		}
	}

	private void openHistorySettings() {
		Fragment target = getTargetFragment();
		if (target instanceof QuickSearchDialogFragment quickSearchDialogFragment) {
			dismissAllowingStateLoss();
			quickSearchDialogFragment.openHistorySettingsAndReturnToSearch();
		} else {
			BaseSettingsFragment.showInstance(requireActivity(), SettingsScreenType.HISTORY_SETTINGS);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (chipsToolbar != null && searchEditText != null) {
			updateSourceChip();
			updateHistoryItems(searchEditText.getText().toString());
		}
		startLocationUpdate();
	}

	@Override
	public void onPause() {
		super.onPause();
		stopLocationUpdate();
	}

	@Override
	public void updateLocation(Location location) {
		if (!MapUtils.areLatLonEqual(this.location, location)) {
			this.location = location;
			updateLocationUi();
			if (selectedSortMode == HistorySortMode.NEAREST) {
				updateHistoryItems(searchEditText.getText().toString());
			}
		}
	}

	@Override
	public void updateCompassValue(float value) {
		float lastHeading = heading != null ? heading : 99;
		heading = value;
		if (Math.abs(MapUtils.degreesDiff(lastHeading, heading)) > 5) {
			updateLocationUi();
		} else {
			heading = lastHeading;
		}
	}

	private void updateLocationUi() {
		if (!touching && compassUpdateAllowed && adapter != null) {
			app.runInUIThread(() -> {
				if (location == null) {
					location = app.getLocationProvider().getLastKnownLocation();
				}
				adapter.notifyDataSetChanged();
			});
		}
	}

	private void startLocationUpdate() {
		if (!locationUpdateStarted) {
			locationUpdateStarted = true;
			OsmAndLocationProvider locationProvider = app.getLocationProvider();
			locationProvider.removeCompassListener(locationProvider.getNavigationInfo());
			locationProvider.addCompassListener(this);
			locationProvider.addLocationListener(this);
			updateLocationUi();
		}
	}

	private void stopLocationUpdate() {
		if (locationUpdateStarted) {
			locationUpdateStarted = false;
			OsmAndLocationProvider locationProvider = app.getLocationProvider();
			locationProvider.removeLocationListener(this);
			locationProvider.removeCompassListener(this);
			locationProvider.addCompassListener(locationProvider.getNavigationInfo());
		}
	}

	@Override
	public InsetTargetsCollection getInsetTargets() {
		InsetTargetsCollection targetsCollection = super.getInsetTargets();
		targetsCollection.replace(InsetTarget.createScrollable(R.id.list));
		return targetsCollection;
	}

	public static void showInstance(@NonNull FragmentManager fragmentManager, @Nullable Fragment target) {
		if (AndroidUtils.isFragmentCanBeAdded(fragmentManager, TAG)) {
			QuickSearchHistoryFragment fragment = new QuickSearchHistoryFragment();
			fragment.setTargetFragment(target, 0);
			fragment.show(fragmentManager, TAG);
		}
	}
}
