package net.osmand.plus.settings.fragments;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.search.SearchBar;

import net.osmand.plus.R;
import net.osmand.plus.base.BaseFullScreenFragment;
import net.osmand.plus.profiles.SelectCopyAppModeBottomSheet;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.coordinates.BuiltInCoordinateFormat;
import net.osmand.plus.settings.coordinates.CoordinateFormat;
import net.osmand.plus.settings.coordinates.CoordinateFormatHelper;
import net.osmand.plus.settings.coordinates.CoordinateFormatIds;
import net.osmand.plus.settings.coordinates.CoordinateFormatPreferences;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.views.controls.ReorderItemTouchHelperCallback;
import net.osmand.plus.widgets.dialogbutton.DialogButton;
import net.osmand.util.Algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CoordinatesFormatFragment extends BaseFullScreenFragment
		implements SelectCopyAppModeBottomSheet.CopyAppModePrefsListener {

	public static final String TAG = CoordinatesFormatFragment.class.getSimpleName();

	private static final String ARG_SCREEN_MODE = "screen_mode";
	private static final String ARG_FOCUS_SEARCH = "focus_search";
	private static final String ARG_ADD_TO_EDIT_DRAFT = "add_to_edit_draft";
	private static final String STATE_EDIT_IDS = "edit_ids";

	private static final String MODE_MAIN = "main";
	private static final String MODE_EDIT = "edit";
	private static final String MODE_ADD = "add";
	static final String REQUEST_ADD_TO_EDIT = "coordinate_format_add_to_edit";
	static final String ADD_SCREEN_BACK_STACK_TAG = TAG + "_" + MODE_ADD;

	private final List<String> editableIds = new ArrayList<>();

	private CoordinateFormatPreferences formatPreferences;
	private CoordinateFormatHelper coordinateFormatHelper;
	private List<String> lastRenderedIds;
	private LinearLayout contentContainer;
	private FrameLayout searchBarContainer;
	private SearchBar searchBar;
	private NestedScrollView scrollView;
	private RecyclerView recyclerView;
	private FloatingActionButton fab;
	private View bottomButtonsContainer;
	private DialogButton applyButton;
	private ItemTouchHelper touchHelper;
	private EditFormatsAdapter editAdapter;

	private String screenMode = MODE_MAIN;
	private boolean focusSearch;
	private boolean addToEditDraft;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		formatPreferences = new CoordinateFormatPreferences(settings);
		coordinateFormatHelper = app.getCoordinateFormatHelper();

		Bundle args = getArguments();
		screenMode = args != null ? args.getString(ARG_SCREEN_MODE, MODE_MAIN) : MODE_MAIN;
		focusSearch = args != null && args.getBoolean(ARG_FOCUS_SEARCH);
		addToEditDraft = args != null && args.getBoolean(ARG_ADD_TO_EDIT_DRAFT);
		ArrayList<String> argEditIds = args != null ? args.getStringArrayList(STATE_EDIT_IDS) : null;
		if (argEditIds != null) {
			editableIds.clear();
			editableIds.addAll(argEditIds);
		}
		if (savedInstanceState != null) {
			screenMode = savedInstanceState.getString(ARG_SCREEN_MODE, screenMode);
			addToEditDraft = savedInstanceState.getBoolean(ARG_ADD_TO_EDIT_DRAFT, addToEditDraft);
			ArrayList<String> savedIds = savedInstanceState.getStringArrayList(STATE_EDIT_IDS);
			if (savedIds != null) {
				editableIds.clear();
				editableIds.addAll(savedIds);
			}
		}

		if (MODE_EDIT.equals(screenMode)) {
			getParentFragmentManager().setFragmentResultListener(REQUEST_ADD_TO_EDIT, this, (requestKey, result) -> {
				String id = result.getString(REQUEST_ADD_TO_EDIT);
				if (!Algorithms.isEmpty(id)) {
					addFormatToEditDraft(id);
				}
			});
		}

		requireMyActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				closeCurrentScreen();
			}
		});
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                         @Nullable Bundle savedInstanceState) {
		updateNightMode();
		View view = LayoutInflater.from(getMaterialThemedContext())
				.inflate(R.layout.coordinate_format_fragment, container, false);
		bindViews(view);
		setupToolbar(view);
		renderScreen();
		return view;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		outState.putString(ARG_SCREEN_MODE, screenMode);
		outState.putBoolean(ARG_ADD_TO_EDIT_DRAFT, addToEditDraft);
		outState.putStringArrayList(STATE_EDIT_IDS, new ArrayList<>(editableIds));
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onResume() {
		super.onResume();
		if ((MODE_MAIN.equals(screenMode) || MODE_ADD.equals(screenMode)) && contentContainer != null
				&& !formatPreferences.getPreferredIds(appMode).equals(lastRenderedIds)) {
			renderScreen();
		}
	}

	@Override
	public void onApplyInsets(@NonNull WindowInsetsCompat insets) {
		super.onApplyInsets(insets);
		if (bottomButtonsContainer != null) {
			int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
			bottomButtonsContainer.setPadding(0, 0, 0, bottomInset);
			if (recyclerView != null && MODE_EDIT.equals(screenMode)) {
				recyclerView.setPadding(0, 0, 0, dp(88) + bottomInset);
			}
		}
	}

	private void bindViews(@NonNull View view) {
		contentContainer = view.findViewById(R.id.content_container);
		searchBarContainer = view.findViewById(R.id.search_bar_container);
		scrollView = view.findViewById(R.id.scroll_view);
		recyclerView = view.findViewById(R.id.recycler_view);
		fab = view.findViewById(R.id.fab);
		bottomButtonsContainer = view.findViewById(R.id.bottom_buttons_container);
		applyButton = view.findViewById(R.id.apply_button);
	}

	private void setupToolbar(@NonNull View view) {
		TextView title = view.findViewById(R.id.toolbar_title);
		TextView subtitle = view.findViewById(R.id.toolbar_subtitle);
		ImageButton closeButton = view.findViewById(R.id.close_button);
		ImageButton editButton = view.findViewById(R.id.action_edit);
		ImageButton addButton = view.findViewById(R.id.action_add);
		ImageButton overflowButton = view.findViewById(R.id.action_overflow);

		if (MODE_ADD.equals(screenMode)) {
			title.setText(R.string.coordinate_format_add_title);
			subtitle.setVisibility(View.GONE);
			closeButton.setImageDrawable(getIcon(R.drawable.ic_action_close, ColorUtilities.getDefaultIconColorId(nightMode)));
			editButton.setVisibility(View.GONE);
			addButton.setVisibility(View.GONE);
			overflowButton.setVisibility(View.GONE);
		} else if (MODE_EDIT.equals(screenMode)) {
			title.setText(R.string.coordinates_format);
			subtitle.setVisibility(View.GONE);
			closeButton.setImageDrawable(getIcon(R.drawable.ic_action_close, ColorUtilities.getDefaultIconColorId(nightMode)));
			editButton.setVisibility(View.GONE);
			addButton.setVisibility(View.VISIBLE);
			overflowButton.setVisibility(View.GONE);
			addButton.setOnClickListener(v -> openScreen(MODE_ADD, false, true));
		} else {
			title.setText(R.string.coordinates_format);
			subtitle.setVisibility(View.GONE);
			closeButton.setImageDrawable(getIcon(AndroidUtils.getNavigationIconResId(app), ColorUtilities.getDefaultIconColorId(nightMode)));
			editButton.setVisibility(View.VISIBLE);
			addButton.setVisibility(View.GONE);
			overflowButton.setVisibility(View.VISIBLE);
			editButton.setOnClickListener(v -> openScreen(MODE_EDIT, false, false));
			overflowButton.setOnClickListener(this::showOverflowMenu);
		}
		closeButton.setOnClickListener(v -> closeCurrentScreen());
	}

	private void renderScreen() {
		lastRenderedIds = formatPreferences.getPreferredIds(appMode);
		contentContainer.removeAllViews();
		searchBarContainer.removeAllViews();
		searchBar = null;
		searchBarContainer.setVisibility(View.GONE);
		recyclerView.setAdapter(null);
		recyclerView.setVisibility(View.GONE);
		scrollView.setVisibility(View.VISIBLE);
		fab.setVisibility(View.GONE);
		bottomButtonsContainer.setVisibility(View.GONE);
		touchHelper = null;

		if (MODE_EDIT.equals(screenMode)) {
			renderEditScreen();
		} else if (MODE_ADD.equals(screenMode)) {
			renderAddScreen();
		} else {
			renderMainScreen();
		}
	}

	private void renderMainScreen() {
		fab.setVisibility(View.VISIBLE);
		fab.setImageDrawable(getIcon(R.drawable.ic_action_add_no_bg, ColorUtilities.getActiveButtonsAndLinksTextColorId(nightMode)));
		fab.setOnClickListener(v -> openScreen(MODE_ADD, false, false));

		TextView description = createText(R.string.coordinate_format_description, 16, 18, 16, 18);
		description.setTextSize(16);
		description.setTextColor(AndroidUtils.getColorFromAttr(requireContext(), android.R.attr.textColorPrimary));
		contentContainer.addView(description);

		List<CoordinateFormat> formats = resolveFormats(formatPreferences.getPreferredIds(appMode));
		if (formats.isEmpty()) {
			return;
		}

		MaterialCardView card = createCard(0, 0, 0, 0);
		LinearLayout list = createVerticalContainer();
		card.addView(list);
		contentContainer.addView(card);

		for (int i = 0; i < formats.size(); i++) {
			CoordinateFormat format = formats.get(i);
			list.addView(createFormatRow(format, false, i == 0, i < formats.size() - 1, v -> {
			}));
		}
	}

	private void renderEditScreen() {
		scrollView.setVisibility(View.GONE);
		recyclerView.setVisibility(View.VISIBLE);
		bottomButtonsContainer.setVisibility(View.VISIBLE);

		if (editableIds.isEmpty()) {
			editableIds.addAll(formatPreferences.getPreferredIds(appMode));
		}
		editAdapter = new EditFormatsAdapter();
		recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
		recyclerView.setAdapter(editAdapter);
		recyclerView.setPadding(0, 0, 0, dp(88) + getBottomSystemInset());

		touchHelper = new ItemTouchHelper(new ReorderItemTouchHelperCallback(editAdapter));
		touchHelper.attachToRecyclerView(recyclerView);

		applyButton.setOnClickListener(v -> applyEditChanges());
		updateApplyButton();
	}

	private void renderAddScreen() {
		searchBar = (SearchBar) LayoutInflater.from(getMaterialThemedContext())
				.inflate(R.layout.coordinate_format_search_bar, searchBarContainer, false);
		FrameLayout.LayoutParams searchParams = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, getDimensionPixelSize(R.dimen.toolbar_height));
		searchParams.setMargins(dp(16), 0, dp(16), dp(16));
		searchBarContainer.setVisibility(View.VISIBLE);
		searchBarContainer.addView(searchBar, searchParams);
		MenuItem searchItem = searchBar.getMenu().findItem(R.id.action_search);
		if (searchItem != null) {
			searchItem.setIcon(getIcon(R.drawable.ic_action_search_dark, ColorUtilities.getDefaultIconColorId(nightMode)));
		}
		bindSearchBarListeners();

		contentContainer.addView(createInfoCard());
		View generalCard = createGeneralCard();
		if (generalCard != null) {
			contentContainer.addView(generalCard);
		}

		if (focusSearch) {
			focusSearch = false;
			searchBar.post(this::openSearchScreen);
		}
	}

	private View createInfoCard() {
		return LayoutInflater.from(getMaterialThemedContext())
				.inflate(R.layout.coordinate_format_add_info_card, contentContainer, false);
	}

	@Nullable
	private View createGeneralCard() {
		List<String> preferredIds = getAddExcludedIds();
		List<CoordinateFormat> formats = new ArrayList<>();
		for (CoordinateFormat format : BuiltInCoordinateFormat.getAll(app)) {
			if (!preferredIds.contains(format.getId())) {
				formats.add(format);
			}
		}
		if (formats.isEmpty()) {
			return null;
		}

		MaterialCardView card = createCard(0, 0, 0, 0);
		LinearLayout list = createVerticalContainer();
		card.addView(list);

		TextView header = createTitleText(getString(R.string.group_general));
		header.setPadding(dp(16), dp(18), dp(16), dp(12));
		list.addView(header);

		for (int i = 0; i < formats.size(); i++) {
			CoordinateFormat format = formats.get(i);
			list.addView(createFormatRow(format, true, false, i < formats.size() - 1, v -> addFormat(format.getId())));
		}
		return card;
	}

	private View createFormatRow(@NonNull CoordinateFormat format, boolean addRow, boolean primary,
	                             boolean showDivider, @NonNull View.OnClickListener clickListener) {
		View row = inflate(R.layout.coordinate_format_settings_item, null, false);
		bindFormatRow(row, format, addRow, primary, showDivider, clickListener);
		return row;
	}

	private void bindFormatRow(@NonNull View row, @NonNull CoordinateFormat format, boolean addRow, boolean primary,
	                           boolean showDivider, @NonNull View.OnClickListener clickListener) {
		TextView title = row.findViewById(android.R.id.title);
		TextView summary = row.findViewById(android.R.id.summary);
		View divider = row.findViewById(R.id.divider);
		View iconContainer = row.findViewById(R.id.iconContainer);
		ImageView icon = row.findViewById(R.id.icon);
		View selectable = row.findViewById(R.id.selectable_list_item);

		title.setText(format.getTitle());
		String description = getFormatSummary(format);
		if (primary) {
			description = description + " • " + getString(R.string.coordinate_format_primary);
		}
		summary.setText(description);
		divider.setVisibility(showDivider ? View.VISIBLE : View.GONE);
		if (addRow) {
			iconContainer.setVisibility(View.VISIBLE);
			iconContainer.setBackground(null);
			icon.setImageDrawable(getIcon(R.drawable.ic_action_add, R.color.color_osm_edit_create));
			setDividerTextStartMargin(divider);
		} else {
			iconContainer.setVisibility(View.GONE);
			setDividerDefaultMargin(divider);
		}
		selectable.setOnClickListener(clickListener);
	}

	private MaterialCardView createCard(int marginStart, int marginTop, int marginEnd, int marginBottom) {
		MaterialCardView card = new MaterialCardView(getMaterialThemedContext());
		card.setCardElevation(0);
		card.setRadius(dp(12));
		card.setStrokeWidth(0);
		card.setUseCompatPadding(false);
		card.setCardBackgroundColor(AndroidUtils.getColorFromAttr(requireContext(), R.attr.list_background_color));
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		params.setMargins(dp(marginStart), dp(marginTop), dp(marginEnd), dp(marginBottom));
		card.setLayoutParams(params);
		return card;
	}

	@NonNull
	private Context getMaterialThemedContext() {
		return UiUtilities.getThemedContext(requireContext(), nightMode,
				R.style.OsmandMaterialLightTheme, R.style.OsmandMaterialDarkTheme);
	}

	private LinearLayout createVerticalContainer() {
		LinearLayout layout = new LinearLayout(requireContext());
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return layout;
	}

	private TextView createText(int textRes, int start, int top, int end, int bottom) {
		TextView text = new TextView(requireContext());
		text.setText(textRes);
		text.setPadding(dp(start), dp(top), dp(end), dp(bottom));
		text.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return text;
	}

	private TextView createTitleText(@NonNull String text) {
		TextView textView = new TextView(requireContext());
		textView.setText(text);
		textView.setTextColor(AndroidUtils.getColorFromAttr(requireContext(), android.R.attr.textColorPrimary));
		textView.setTextSize(16);
		textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
		return textView;
	}

	private int getBottomSystemInset() {
		WindowInsetsCompat insets = getLastRootInsets();
		return insets != null ? insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom : 0;
	}

	private String getFormatSummary(@NonNull CoordinateFormat format) {
		return coordinateFormatHelper.getFormatSummary(format);
	}

	private List<CoordinateFormat> resolveFormats(@NonNull List<String> ids) {
		return coordinateFormatHelper.resolveFormats(ids);
	}

	private void addFormat(@NonNull String id) {
		if (addToEditDraft) {
			Bundle result = new Bundle();
			result.putString(REQUEST_ADD_TO_EDIT, id);
			getParentFragmentManager().setFragmentResult(REQUEST_ADD_TO_EDIT, result);
			dismiss();
			return;
		}
		boolean added = formatPreferences.addPreferredId(appMode, id);
		if (added) {
			formatPreferences.addRecentId(id);
			syncLegacyPrimary(formatPreferences.getPreferredIds(appMode));
			renderScreen();
		}
	}

	private void addFormatToEditDraft(@NonNull String id) {
		String normalizedId = CoordinateFormatIds.normalize(id);
		if (Algorithms.isEmpty(normalizedId) || editableIds.contains(normalizedId)) {
			return;
		}
		editableIds.add(normalizedId);
		if (editAdapter != null) {
			editAdapter.notifyItemInserted(editableIds.size() - 1);
		}
		updateApplyButton();
	}

	@NonNull
	private List<String> getAddExcludedIds() {
		return addToEditDraft ? editableIds : formatPreferences.getPreferredIds(appMode);
	}

	@Override
	public void copyAppModePrefs(@NonNull ApplicationMode fromMode) {
		formatPreferences.copyPreferredIds(fromMode, appMode);
		syncLegacyPrimary(formatPreferences.getPreferredIds(appMode));
		renderScreen();
	}

	private void syncLegacyPrimary(@NonNull List<String> ids) {
		coordinateFormatHelper.syncLegacyPrimary(appMode, ids);
	}

	private void resetToDefault() {
		formatPreferences.resetPreferredIds(appMode);
		syncLegacyPrimary(formatPreferences.getPreferredIds(appMode));
		renderScreen();
	}

	private void applyEditChanges() {
		if (!isEditChanged()) {
			return;
		}
		List<String> previousIds = formatPreferences.getPreferredIds(appMode);
		formatPreferences.setPreferredIds(appMode, editableIds);
		for (String id : editableIds) {
			if (!previousIds.contains(id)) {
				formatPreferences.addRecentId(id);
			}
		}
		syncLegacyPrimary(editableIds);
		dismiss();
	}

	private boolean isEditChanged() {
		return !editableIds.equals(formatPreferences.getPreferredIds(appMode));
	}

	private void updateApplyButton() {
		applyButton.setEnabled(isEditChanged());
	}

	private void closeCurrentScreen() {
		if (MODE_EDIT.equals(screenMode) && isEditChanged()) {
			new AlertDialog.Builder(UiUtilities.getThemedContext(requireContext(), nightMode))
					.setTitle(R.string.coordinate_format_cancel_changes_title)
					.setMessage(R.string.coordinate_format_cancel_changes_message)
					.setPositiveButton(R.string.coordinate_format_discard_changes, (dialog, which) -> dismiss())
					.setNegativeButton(R.string.shared_string_cancel, null)
					.show();
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

	private void openScreen(@NonNull String mode, boolean requestSearchFocus, boolean openAddForEditDraft) {
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		CoordinatesFormatFragment fragment = new CoordinatesFormatFragment();
		Bundle args = new Bundle();
		args.putString(APP_MODE_KEY, appMode.getStringKey());
		args.putString(ARG_SCREEN_MODE, mode);
		args.putBoolean(ARG_FOCUS_SEARCH, requestSearchFocus);
		args.putBoolean(ARG_ADD_TO_EDIT_DRAFT, openAddForEditDraft);
		if (openAddForEditDraft) {
			args.putStringArrayList(STATE_EDIT_IDS, new ArrayList<>(editableIds));
		}
		fragment.setArguments(args);
		activity.getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragmentContainer, fragment, TAG + "_" + mode)
				.addToBackStack(TAG + "_" + mode)
				.commitAllowingStateLoss();
	}

	private void openSearchScreen() {
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		CoordinateFormatSearchFragment.show(activity, appMode, getAddExcludedIds(), addToEditDraft);
	}

	private void bindSearchBarListeners() {
		if (searchBar == null) {
			return;
		}
		searchBar.setOnClickListener(v -> openSearchScreen());
		searchBar.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == R.id.action_search) {
				openSearchScreen();
				return true;
			}
			return false;
		});
	}

	public static void showAddFormat(@NonNull FragmentActivity activity, @NonNull ApplicationMode appMode) {
		CoordinatesFormatFragment fragment = new CoordinatesFormatFragment();
		Bundle args = new Bundle();
		args.putString(APP_MODE_KEY, appMode.getStringKey());
		args.putString(ARG_SCREEN_MODE, MODE_ADD);
		args.putBoolean(ARG_FOCUS_SEARCH, true);
		fragment.setArguments(args);
		activity.getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragmentContainer, fragment, TAG + "_" + MODE_ADD)
				.addToBackStack(ADD_SCREEN_BACK_STACK_TAG)
				.commitAllowingStateLoss();
	}

	private void showOverflowMenu(@NonNull View anchor) {
		PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
		popupMenu.getMenu().add(0, R.string.reset_to_default, 0, R.string.reset_to_default)
				.setIcon(R.drawable.ic_action_reset_to_default_dark);
		popupMenu.getMenu().add(0, R.string.copy_from_other_profile, 1, R.string.copy_from_other_profile)
				.setIcon(R.drawable.ic_action_copy);
		popupMenu.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == R.string.reset_to_default) {
				resetToDefault();
				return true;
			} else if (item.getItemId() == R.string.copy_from_other_profile) {
				SelectCopyAppModeBottomSheet.showInstance(getParentFragmentManager(), this, appMode);
				return true;
			}
			return false;
		});
		popupMenu.show();
	}

	private int dp(float value) {
		return AndroidUtils.dpToPx(app, value);
	}

	private void setDividerTextStartMargin(@NonNull View divider) {
		setDividerStartMargin(divider, dp(72));
	}

	private void setDividerDefaultMargin(@NonNull View divider) {
		setDividerStartMargin(divider, dp(16));
	}

	private void setDividerStartMargin(@NonNull View divider, int marginStart) {
		ViewGroup.LayoutParams params = divider.getLayoutParams();
		if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
			marginParams.setMarginStart(marginStart);
			divider.setLayoutParams(marginParams);
		}
	}

	private class EditFormatsAdapter extends RecyclerView.Adapter<EditFormatsAdapter.FormatViewHolder>
			implements ReorderItemTouchHelperCallback.OnItemMoveCallback {

		@NonNull
		@Override
		public FormatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = inflate(R.layout.coordinate_format_edit_item, parent, false);
			return new FormatViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull FormatViewHolder holder, int position) {
			CoordinateFormat format = resolveFormats(Collections.singletonList(editableIds.get(position))).get(0);
			holder.title.setText(format.getTitle());
			String summary = getFormatSummary(format);
			if (position == 0) {
				summary = summary + " • " + getString(R.string.coordinate_format_primary);
			}
			holder.summary.setText(summary);
			holder.divider.setVisibility(position < getItemCount() - 1 ? View.VISIBLE : View.GONE);
			holder.removeButton.setEnabled(editableIds.size() > 1);
			holder.removeButton.setAlpha(editableIds.size() > 1 ? 1.0f : 0.35f);
			holder.removeButton.setBackground(null);
			holder.removeButton.setOnClickListener(v -> removeItem(holder.getBindingAdapterPosition()));
			holder.dragHandle.setOnTouchListener((v, event) -> {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN && touchHelper != null) {
					touchHelper.startDrag(holder);
				}
				return false;
			});
		}

		@Override
		public int getItemCount() {
			return editableIds.size();
		}

		@Override
		public boolean onItemMove(int from, int to) {
			if (from < 0 || to < 0 || from >= editableIds.size() || to >= editableIds.size()) {
				return false;
			}
			Collections.swap(editableIds, from, to);
			notifyItemMoved(from, to);
			updateApplyButton();
			return true;
		}

		@Override
		public void onItemDismiss(@NonNull RecyclerView.ViewHolder holder) {
			updateApplyButton();
		}

		private void removeItem(int position) {
			if (position == RecyclerView.NO_POSITION) {
				return;
			}
			if (editableIds.size() <= 1) {
				app.showShortToastMessage(R.string.coordinate_format_last_item_warning);
				return;
			}
			editableIds.remove(position);
			notifyItemRemoved(position);
			notifyItemRangeChanged(position, editableIds.size() - position);
			updateApplyButton();
		}

		private static class FormatViewHolder extends RecyclerView.ViewHolder {
			private final TextView title;
			private final TextView summary;
			private final View divider;
			private final ImageButton removeButton;
			private final ImageButton dragHandle;

			FormatViewHolder(@NonNull View itemView) {
				super(itemView);
				title = itemView.findViewById(android.R.id.title);
				summary = itemView.findViewById(android.R.id.summary);
				divider = itemView.findViewById(R.id.divider);
				removeButton = itemView.findViewById(R.id.removeButton);
				dragHandle = itemView.findViewById(R.id.dragHandle);
			}
		}
	}
}