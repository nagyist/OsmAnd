package net.osmand.plus.settings.fragments;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;

import net.osmand.plus.R;
import net.osmand.plus.base.BaseFullScreenFragment;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.coordinates.CoordinateFormat;
import net.osmand.plus.settings.coordinates.CoordinateFormatHelper;
import net.osmand.plus.settings.coordinates.CoordinateFormatIds;
import net.osmand.plus.settings.coordinates.CoordinateFormatPreferences;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.utils.InsetTarget;
import net.osmand.plus.utils.InsetTargetsCollection;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.widgets.tools.SimpleTextWatcher;

import java.util.ArrayList;
import java.util.List;

public class CoordinateFormatSearchFragment extends BaseFullScreenFragment {

	public static final String TAG = CoordinateFormatSearchFragment.class.getSimpleName();

	private static final String ARG_EXCLUDED_IDS = "excluded_ids";
	private static final String ARG_ADD_TO_EDIT_DRAFT = "add_to_edit_draft";
	private static final String STATE_SEARCH_QUERY = "search_query";
	private static final int SOFT_INPUT_MODE_NOT_SET = Integer.MIN_VALUE;

	private final List<String> excludedIds = new ArrayList<>();
	private final List<CoordinateFormat> searchItems = new ArrayList<>();

	private CoordinateFormatPreferences formatPreferences;
	private CoordinateFormatHelper coordinateFormatHelper;
	private SearchBar searchBar;
	private SearchView searchInputView;
	private RecyclerView searchResults;
	private SearchResultsAdapter searchResultsAdapter;

	private String searchQuery = "";
	private boolean addToEditDraft;
	private int previousSoftInputMode = SOFT_INPUT_MODE_NOT_SET;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		formatPreferences = new CoordinateFormatPreferences(settings);
		coordinateFormatHelper = app.getCoordinateFormatHelper();

		Bundle args = getArguments();
		addToEditDraft = args != null && args.getBoolean(ARG_ADD_TO_EDIT_DRAFT);
		ArrayList<String> argExcludedIds = args != null ? args.getStringArrayList(ARG_EXCLUDED_IDS) : null;
		if (argExcludedIds != null) {
			excludedIds.addAll(argExcludedIds);
		}
		if (savedInstanceState != null) {
			searchQuery = savedInstanceState.getString(STATE_SEARCH_QUERY, "");
			ArrayList<String> savedExcludedIds = savedInstanceState.getStringArrayList(ARG_EXCLUDED_IDS);
			if (savedExcludedIds != null) {
				excludedIds.clear();
				excludedIds.addAll(savedExcludedIds);
			}
		}

		requireMyActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				closeSearch();
			}
		});
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                         @Nullable Bundle savedInstanceState) {
		updateNightMode();
		View view = LayoutInflater.from(getMaterialThemedContext())
				.inflate(R.layout.coordinate_format_database_search_fragment, container, false);
		bindViews(view);
		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		AndroidUiHelper.setStatusBarContentColor(view, nightMode);
		searchBar.post(() -> {
			if (getView() != null && !searchInputView.isShowing()) {
				searchInputView.show();
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		applySearchSoftInputMode();
	}

	@Override
	public void onDestroyView() {
		coordinateFormatHelper.cancelSearch();
		restoreSearchSoftInputMode();
		super.onDestroyView();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		outState.putString(STATE_SEARCH_QUERY, searchQuery);
		outState.putStringArrayList(ARG_EXCLUDED_IDS, new ArrayList<>(excludedIds));
		super.onSaveInstanceState(outState);
	}

	@Override
	public InsetTargetsCollection getInsetTargets() {
		InsetTargetsCollection collection = super.getInsetTargets();
		collection.removeType(InsetTarget.Type.ROOT_INSET);
		collection.add(InsetTarget.createScrollable(R.id.search_results));
		collection.replace(InsetTarget.createCollapsingAppBar(R.id.search_app_bar));
		return collection;
	}

	@Override
	@ColorRes
	public int getStatusBarColorId() {
		return nightMode ? R.color.activity_background_color_dark : R.color.activity_background_color_light;
	}

	private void bindViews(@NonNull View view) {
		searchBar = view.findViewById(R.id.search_anchor_bar);
		searchInputView = view.findViewById(R.id.search_input_view);
		searchResults = view.findViewById(R.id.search_results);
		searchResultsAdapter = new SearchResultsAdapter();
		searchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
		searchResults.setAdapter(searchResultsAdapter);
		searchResults.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
				if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
					AndroidUtils.hideSoftKeyboard(requireMyActivity(), searchInputView.getEditText());
				}
			}
		});

		searchInputView.setVisible(false);
		searchInputView.setupWithSearchBar(searchBar);
		searchInputView.setMenuItemsAnimated(false);
		searchInputView.setAutoShowKeyboard(true);
		searchInputView.updateSoftInputMode();

		View divider = searchInputView.findViewById(com.google.android.material.R.id.open_search_view_divider);
		if (divider != null) {
			divider.setVisibility(View.GONE);
		}
		searchInputView.getToolbar().setElevation(0);
		searchInputView.getToolbar().setTranslationZ(0);
		searchInputView.getToolbar().getMenu().clear();
		if (searchInputView.getToolbar().getNavigationIcon() != null) {
			searchInputView.getToolbar().getNavigationIcon().mutate().setTint(
					ColorUtilities.getDefaultIconColor(requireContext(), nightMode));
		}
		searchInputView.addTransitionListener((searchView, previousState, newState) -> {
			if (newState == SearchView.TransitionState.SHOWN) {
				searchInputView.getEditText().setSelection(searchInputView.getEditText().length());
			} else if (newState == SearchView.TransitionState.HIDDEN) {
				dismiss();
			}
		});

		searchInputView.getEditText().setText(searchQuery);
		searchInputView.getEditText().setSelection(searchInputView.getEditText().length());
		searchInputView.getEditText().addTextChangedListener(new SimpleTextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				searchQuery = s.toString();
				renderSearchResults();
			}
		});
		renderSearchResults();
	}

	private void closeSearch() {
		if (searchInputView != null && searchInputView.isShowing()) {
			searchInputView.hide();
		} else {
			dismiss();
		}
	}

	private void dismiss() {
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		FragmentManager fragmentManager = activity.getSupportFragmentManager();
		if (!fragmentManager.isStateSaved()) {
			fragmentManager.popBackStack();
		}
	}

	private void selectSearchFormat(@NonNull CoordinateFormat format) {
		String id = format.getId();
		if (addToEditDraft) {
			Bundle result = new Bundle();
			result.putString(CoordinatesFormatFragment.REQUEST_ADD_TO_EDIT, id);
			getParentFragmentManager().setFragmentResult(CoordinatesFormatFragment.REQUEST_ADD_TO_EDIT, result);
			FragmentManager fragmentManager = getParentFragmentManager();
			if (!fragmentManager.isStateSaved()) {
				fragmentManager.popBackStack(CoordinatesFormatFragment.ADD_SCREEN_BACK_STACK_TAG,
						FragmentManager.POP_BACK_STACK_INCLUSIVE);
			}
			return;
		}
		boolean added = formatPreferences.addPreferredId(appMode, id);
		if (added) {
			String normalizedId = CoordinateFormatIds.normalize(id);
			excludedIds.add(normalizedId != null ? normalizedId : id);
			formatPreferences.addRecentId(id);
			syncLegacyPrimary(formatPreferences.getPreferredIds(appMode));
			renderSearchResults();
		}
	}

	private void syncLegacyPrimary(@NonNull List<String> ids) {
		coordinateFormatHelper.syncLegacyPrimary(appMode, ids);
	}

	private void renderSearchResults() {
		if (searchResultsAdapter == null) {
			return;
		}
		String query = searchQuery;
		coordinateFormatHelper.searchFormats(query, results -> {
			if (searchResultsAdapter == null || !query.equals(searchQuery)) {
				return;
			}
			searchItems.clear();
			for (CoordinateFormat format : results) {
				if (!excludedIds.contains(format.getId())) {
					searchItems.add(format);
				}
			}
			searchResultsAdapter.notifyDataSetChanged();
		});
	}

	private String getFormatSummary(@NonNull CoordinateFormat format) {
		return coordinateFormatHelper.getFormatSummary(format);
	}

	@NonNull
	private Context getMaterialThemedContext() {
		return UiUtilities.getThemedContext(requireContext(), nightMode,
				R.style.OsmandMaterialLightTheme, R.style.OsmandMaterialDarkTheme);
	}

	private void setDividerTextStartMargin(@NonNull View divider) {
		ViewGroup.LayoutParams params = divider.getLayoutParams();
		if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
			marginParams.setMarginStart(AndroidUtils.dpToPx(app, 72));
			divider.setLayoutParams(marginParams);
		}
	}

	private void applySearchSoftInputMode() {
		Activity activity = getActivity();
		Window window = activity != null ? activity.getWindow() : null;
		if (window == null) {
			return;
		}
		if (previousSoftInputMode == SOFT_INPUT_MODE_NOT_SET) {
			previousSoftInputMode = window.getAttributes().softInputMode;
		}
		window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
		if (searchInputView != null) {
			searchInputView.updateSoftInputMode();
		}
	}

	private void restoreSearchSoftInputMode() {
		Activity activity = getActivity();
		Window window = activity != null ? activity.getWindow() : null;
		if (window == null || previousSoftInputMode == SOFT_INPUT_MODE_NOT_SET) {
			return;
		}
		window.setSoftInputMode(previousSoftInputMode);
		previousSoftInputMode = SOFT_INPUT_MODE_NOT_SET;
	}

	public static void show(@NonNull FragmentActivity activity, @NonNull ApplicationMode appMode,
	                        @NonNull List<String> excludedIds, boolean addToEditDraft) {
		CoordinateFormatSearchFragment fragment = new CoordinateFormatSearchFragment();
		Bundle args = new Bundle();
		args.putString(APP_MODE_KEY, appMode.getStringKey());
		args.putStringArrayList(ARG_EXCLUDED_IDS, new ArrayList<>(excludedIds));
		args.putBoolean(ARG_ADD_TO_EDIT_DRAFT, addToEditDraft);
		fragment.setArguments(args);
		activity.getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragmentContainer, fragment, TAG)
				.addToBackStack(TAG)
				.commitAllowingStateLoss();
	}

	private class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.FormatViewHolder> {

		@NonNull
		@Override
		public FormatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = inflate(R.layout.coordinate_format_settings_item, parent, false);
			return new FormatViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull FormatViewHolder holder, int position) {
			CoordinateFormat format = searchItems.get(position);
			holder.bind(format, position < getItemCount() - 1);
		}

		@Override
		public int getItemCount() {
			return searchItems.size();
		}

		private class FormatViewHolder extends RecyclerView.ViewHolder {
			private final TextView title;
			private final TextView summary;
			private final View divider;
			private final View iconContainer;
			private final ImageView icon;
			private final View selectable;

			FormatViewHolder(@NonNull View itemView) {
				super(itemView);
				title = itemView.findViewById(android.R.id.title);
				summary = itemView.findViewById(android.R.id.summary);
				divider = itemView.findViewById(R.id.divider);
				iconContainer = itemView.findViewById(R.id.iconContainer);
				icon = itemView.findViewById(R.id.icon);
				selectable = itemView.findViewById(R.id.selectable_list_item);
			}

			void bind(@NonNull CoordinateFormat format, boolean showDivider) {
				title.setText(format.getTitle());
				summary.setText(getFormatSummary(format));
				divider.setVisibility(showDivider ? View.VISIBLE : View.GONE);
				iconContainer.setVisibility(View.VISIBLE);
				iconContainer.setBackground(null);
				icon.setImageDrawable(getIcon(R.drawable.ic_action_add, R.color.color_osm_edit_create));
				setDividerTextStartMargin(divider);
				selectable.setOnClickListener(v -> selectSearchFormat(format));
			}
		}
	}
}
