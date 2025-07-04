package net.osmand.plus.myplaces.tracks.dialogs;

import static net.osmand.plus.configmap.tracks.PreselectedTabParams.CALLING_FRAGMENT_TAG;
import static net.osmand.plus.configmap.tracks.PreselectedTabParams.PRESELECTED_TRACKS_TAB_ID;
import static net.osmand.plus.configmap.tracks.PreselectedTabParams.SELECT_ALL_ITEMS_ON_TAB;
import static net.osmand.plus.configmap.tracks.TrackTabType.FOLDER;
import static net.osmand.plus.configmap.tracks.TrackTabType.SMART_FOLDER;
import static net.osmand.plus.importfiles.ImportHelper.IMPORT_FILE_REQUEST;
import static net.osmand.plus.myplaces.MyPlacesActivity.GPX_TAB;
import static net.osmand.plus.myplaces.MyPlacesActivity.TAB_ID;
import static net.osmand.plus.myplaces.tracks.dialogs.TrackFoldersAdapter.TYPE_EMPTY_FOLDER;
import static net.osmand.plus.myplaces.tracks.dialogs.TrackFoldersAdapter.TYPE_EMPTY_SMART_FOLDER;
import static net.osmand.plus.myplaces.tracks.dialogs.TrackFoldersAdapter.TYPE_SORT_TRACKS;
import static net.osmand.plus.track.fragments.TrackMenuFragment.TrackMenuTab.OVERVIEW;
import static net.osmand.plus.utils.AndroidUtils.getViewOnScreenY;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import net.osmand.data.LatLon;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.configmap.tracks.SortByBottomSheet;
import net.osmand.plus.configmap.tracks.TrackSortModesHelper;
import net.osmand.plus.configmap.tracks.TrackTabType;
import net.osmand.plus.configmap.tracks.TracksComparator;
import net.osmand.plus.configmap.tracks.appearance.DefaultAppearanceController;
import net.osmand.plus.configmap.tracks.viewholders.EmptyTracksViewHolder.EmptyTracksListener;
import net.osmand.plus.configmap.tracks.viewholders.SortTracksViewHolder.SortTracksListener;
import net.osmand.plus.configmap.tracks.viewholders.TrackViewHolder.TrackSelectionListener;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.importfiles.GpxImportListener;
import net.osmand.plus.importfiles.ImportHelper;
import net.osmand.plus.myplaces.MyPlacesActivity;
import net.osmand.plus.myplaces.favorites.dialogs.FragmentStateHolder;
import net.osmand.plus.myplaces.tracks.DialogClosedListener;
import net.osmand.plus.myplaces.tracks.ItemsSelectionHelper.SelectionHelperProvider;
import net.osmand.plus.myplaces.tracks.TrackFoldersHelper;
import net.osmand.plus.myplaces.tracks.TracksSearchFilter;
import net.osmand.plus.myplaces.tracks.controller.SmartFolderOptionsController;
import net.osmand.plus.myplaces.tracks.controller.SmartFolderOptionsListener;
import net.osmand.plus.myplaces.tracks.controller.TrackFolderOptionsController;
import net.osmand.plus.myplaces.tracks.controller.TrackFolderOptionsListener;
import net.osmand.plus.myplaces.tracks.dialogs.AddNewTrackFolderBottomSheet.OnTrackFolderAddListener;
import net.osmand.plus.myplaces.tracks.dialogs.MoveGpxFileBottomSheet.OnTrackFileMoveListener;
import net.osmand.plus.myplaces.tracks.dialogs.viewholders.TracksGroupViewHolder.TrackGroupsListener;
import net.osmand.plus.plugins.osmedit.oauth.OsmOAuthHelper.OsmAuthorizationListener;
import net.osmand.plus.settings.enums.TracksSortMode;
import net.osmand.plus.shared.SharedUtil;
import net.osmand.plus.track.fragments.TrackMenuFragment;
import net.osmand.plus.track.helpers.GpxSelectionHelper;
import net.osmand.plus.track.helpers.SelectGpxTask.SelectGpxTaskListener;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.utils.FileUtils.RenameCallback;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.shared.gpx.SmartFolderHelper;
import net.osmand.shared.gpx.TrackItem;
import net.osmand.shared.gpx.data.SmartFolder;
import net.osmand.shared.gpx.data.TrackFolder;
import net.osmand.shared.gpx.data.TracksGroup;
import net.osmand.shared.gpx.filters.TrackFolderAnalysis;
import net.osmand.shared.io.KFile;
import net.osmand.shared.util.KAlgorithms;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class BaseTrackFolderFragment extends BaseOsmAndFragment implements FragmentStateHolder,
		SortTracksListener, TrackSelectionListener, TrackGroupsListener, EmptyTracksListener, OsmAuthorizationListener,
		SelectGpxTaskListener, OnTrackFolderAddListener, GpxImportListener, TrackFolderOptionsListener,
		OnTrackFileMoveListener, RenameCallback, SelectionHelperProvider<TrackItem>, SmartFolderOptionsListener {

	private static final String TAG = BaseTrackFolderFragment.class.getSimpleName();

	public static final String SELECTED_SMART_FOLDER_KEY = "selected_smart_folder_key";
	public static final String SELECTED_FOLDER_KEY = "selected_folder_key";
	public static final String SELECTED_ITEM_PATH_KEY = "selected_item_path_key";

	protected GpxSelectionHelper gpxSelectionHelper;

	protected TrackFolder rootFolder;
	protected TrackFolder selectedFolder;
	protected SmartFolder smartFolder;

	protected TrackFoldersAdapter adapter;
	protected RecyclerView recyclerView;
	protected String selectedItemPath;
	protected String preSelectedFolder;
	protected SmartFolderHelper smartFolderHelper;

	@ColorRes
	public int getStatusBarColorId() {
		AndroidUiHelper.setStatusBarContentColor(getView(), nightMode);
		return ColorUtilities.getStatusBarColorId(nightMode);
	}

	public boolean getContentStatusBarNightMode() {
		return nightMode;
	}

	protected abstract int getLayoutId();

	@NonNull
	protected abstract String getFragmentTag();

	@Nullable
	public TrackFolder getRootFolder() {
		return rootFolder;
	}

	@Nullable
	public TrackFolder getSelectedFolder() {
		return selectedFolder;
	}

	public void setRootFolder(@NonNull TracksGroup rootFolder) {
		if (rootFolder instanceof TrackFolder) {
			this.rootFolder = (TrackFolder) rootFolder;
		}
	}

	public void setSelectedFolder(@NonNull TrackFolder selectedFolder) {
		this.selectedFolder = selectedFolder;
	}

	public void setSmartFolder(SmartFolder folder) {
		smartFolder = folder;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		gpxSelectionHelper = app.getSelectedGpxHelper();
		smartFolderHelper = app.getSmartFolderHelper();
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		FragmentActivity activity = requireActivity();
		LayoutInflater themedInflater = UiUtilities.getInflater(activity, nightMode);
		View view = themedInflater.inflate(getLayoutId(), container, false);

		setupAdapter(view);

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		TrackFoldersHelper foldersHelper = getTrackFoldersHelper();
		if (foldersHelper != null) {
			foldersHelper.setGpxImportListener(this);
		}
		gpxSelectionHelper.addListener(this);
	}

	@Override
	public void onPause() {
		super.onPause();

		TrackFoldersHelper foldersHelper = getTrackFoldersHelper();
		if (foldersHelper != null) {
			foldersHelper.setGpxImportListener(null);
		}
		gpxSelectionHelper.removeListener(this);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == IMPORT_FILE_REQUEST && resultCode == Activity.RESULT_OK && selectedFolder != null) {
			TrackFoldersHelper foldersHelper = getTrackFoldersHelper();
			if (foldersHelper != null) {
				foldersHelper.handleImport(data, SharedUtil.jFile(selectedFolder.getDirFile()));
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@Nullable
	public TrackFoldersHelper getTrackFoldersHelper() {
		Fragment target = getTargetFragment();
		if (target instanceof BaseTrackFolderFragment) {
			return ((BaseTrackFolderFragment) target).getTrackFoldersHelper();
		}
		return null;
	}

	protected void setupAdapter(@NonNull View view) {
		adapter = new TrackFoldersAdapter(view.getContext(), nightMode, selectedFolder);
		adapter.setSortTracksListener(this);
		adapter.setTrackGroupsListener(this);
		adapter.setTrackSelectionListener(this);
		adapter.setEmptyTracksListener(this);

		recyclerView = view.findViewById(R.id.recycler_view);
		recyclerView.setLayoutManager(new LinearLayoutManager(app));
		recyclerView.setItemAnimator(null);
		recyclerView.setAdapter(adapter);
	}

	@NonNull
	protected List<Object> getAdapterItems() {
		List<Object> items = new ArrayList<>();
		items.add(TYPE_SORT_TRACKS);

		List<TrackFolder> folders = null;
		List<TrackItem> trackItems = null;
		if (selectedFolder == null) {
			if (smartFolder != null) {
				trackItems = smartFolder.getTrackItems();
			}
		} else {
			folders = selectedFolder.getSubFolders();
			trackItems = selectedFolder.getTrackItems();
		}
		if (KAlgorithms.INSTANCE.isEmpty(folders) && KAlgorithms.INSTANCE.isEmpty(trackItems)) {
			items.add(getEmptyItem());
		} else {
			if (!KAlgorithms.INSTANCE.isEmpty(folders)) {
				items.addAll(folders);
			}
			items.addAll(trackItems);

			if (shouldShowFolderStats()) {
				TracksGroup tracksGroup = selectedFolder;
				if (tracksGroup == null) {
					tracksGroup = smartFolder;
				}
				if (tracksGroup != null) {
					items.add(new TrackFolderAnalysis(tracksGroup));
				}
			}
		}
		return items;
	}

	protected Object getEmptyItem() {
		return smartFolder == null ? TYPE_EMPTY_FOLDER : TYPE_EMPTY_SMART_FOLDER;
	}

	protected boolean shouldShowFolderStats() {
		return true;
	}

	public void updateContent() {
		List<Object> items = getAdapterItems();
		TracksSortMode sortMode = getTracksSortMode();
		sortItems(items, sortMode);

		adapter.setSortMode(sortMode);
		adapter.setItems(items);
	}

	private void sortItems(@NonNull List<Object> items, @NonNull TracksSortMode sortMode) {
		LatLon latLon = app.getMapViewTrackingUtilities().getDefaultLocation();
		Collections.sort(items, new TracksComparator(sortMode, latLon));
	}

	public void showTrackOnMap(@NonNull TrackItem trackItem) {
		FragmentActivity activity = getActivity();
		if (activity != null) {
			selectedItemPath = trackItem.getPath();

			Bundle bundle = storeState();
			String screenName = app.getString(R.string.shared_string_tracks);
			boolean temporary = gpxSelectionHelper.getSelectedFileByPath(trackItem.getPath()) == null;
			KFile file = trackItem.getFile();

			TrackMenuFragment.openTrack(activity, file != null ? SharedUtil.jFile(file) : null,
					bundle, screenName, OVERVIEW, temporary);
		}
	}

	public void dismiss() {
		FragmentActivity activity = getActivity();
		if (activity != null) {
			FragmentManager fragmentManager = activity.getSupportFragmentManager();
			if (!fragmentManager.isStateSaved()) {
				fragmentManager.popBackStack(getFragmentTag(), FragmentManager.POP_BACK_STACK_INCLUSIVE);
			}
		}
	}

	@Override
	public void onTracksGroupOptionsSelected(@NonNull View view, @NonNull TracksGroup group) {
		TrackFoldersHelper foldersHelper = getTrackFoldersHelper();
		if (foldersHelper != null && group instanceof TrackFolder) {
			TrackFolder folder = (TrackFolder) group;
			TrackFolderOptionsController.showDialog(foldersHelper, folder, this);
		} else if (group instanceof SmartFolder) {
			SmartFolder folder = (SmartFolder) group;
			SmartFolderOptionsController.Companion.showDialog(app, getChildFragmentManager(), folder, this);
		}
	}

	@Override
	public void showSortByDialog() {
		FragmentManager manager = getFragmentManager();
		if (manager != null) {
			SortByBottomSheet.showInstance(manager, getTracksSortMode(), this, isUsedOnMap());
		}
	}

	@NonNull
	@Override
	public TracksSortMode getTracksSortMode() {
		TrackSortModesHelper sortModesHelper = app.getTrackSortModesHelper();
		return sortModesHelper.requireSortMode(getSortEntryId());
	}

	@Nullable
	protected String getSortEntryId() {
		TracksGroup folder = selectedFolder != null ? selectedFolder : smartFolder;
		return folder != null ? folder.getId() : null;
	}

	@Override
	public void setTracksSortMode(@NonNull TracksSortMode sortMode, boolean sortSubFolders) {
		if (sortSubFolders) {
			sortSubFolder(sortMode);
		} else {
			TracksGroup folder = smartFolder != null ? smartFolder : selectedFolder;
			TrackSortModesHelper sortModesHelper = app.getTrackSortModesHelper();
			sortModesHelper.setSortMode(folder.getId(), sortMode);
			sortModesHelper.syncSettings();
			updateContent();
		}
	}

	private void sortSubFolder(@NonNull TracksSortMode sortMode) {
		TrackSortModesHelper sortModesHelper = app.getTrackSortModesHelper();
		sortFolders(selectedFolder, sortModesHelper, sortMode);
		sortModesHelper.syncSettings();

		app.showToastMessage(app.getString(R.string.sorted_sufolders_toast, selectedFolder.getName(), app.getString(sortMode.getNameId())));
	}

	private void sortFolders(@NonNull TrackFolder trackFolder,
	                         @NonNull TrackSortModesHelper sortModesHelper,
	                         @NonNull TracksSortMode sortMode) {
		for (TrackFolder folder : trackFolder.getFlattenedSubFolders()) {
			sortModesHelper.setSortMode(folder.getId(), sortMode);
		}
	}

	@Override
	public void importTracks() {
		Intent intent = ImportHelper.getImportFileIntent();
		intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		AndroidUtils.startActivityForResultIfSafe(this, intent, IMPORT_FILE_REQUEST);
	}

	@Override
	public void fileRenamed(@NonNull File src, @NonNull File dest) {
		reloadTracks();
	}

	@Override
	public void onFileMove(@Nullable File src, @NonNull File dest) {
		TrackFoldersHelper foldersHelper = getTrackFoldersHelper();
		if (foldersHelper != null) {
			foldersHelper.onFileMove(src, dest);
		}
	}

	@Override
	public void onTrackFolderAdd(String folderName) {
		File dir = new File(SharedUtil.jFile(selectedFolder.getDirFile()), folderName);
		if (!dir.exists()) {
			dir.mkdirs();
			dir.setLastModified(System.currentTimeMillis());
		}
		reloadTracks(true);
	}

	@Override
	public Bundle storeState() {
		Bundle bundle = new Bundle();
		bundle.putInt(TAB_ID, GPX_TAB);
		bundle.putString(SELECTED_ITEM_PATH_KEY, selectedItemPath);
		if (selectedFolder != null) {
			bundle.putString(SELECTED_FOLDER_KEY, selectedFolder.getDirFile().absolutePath());
		}
		if (smartFolder != null) {
			bundle.putString(SELECTED_SMART_FOLDER_KEY, smartFolder.getFolderName());
		}
		return bundle;
	}

	@Override
	public void restoreState(Bundle bundle) {
		if (bundle != null && bundle.getInt(TAB_ID) == GPX_TAB) {
			preSelectedFolder = bundle.getString(SELECTED_FOLDER_KEY);
			selectedItemPath = bundle.getString(SELECTED_ITEM_PATH_KEY);

			String smartFolderName = bundle.getString(SELECTED_SMART_FOLDER_KEY);
			if (smartFolderName != null) {
				smartFolder = app.getSmartFolderHelper().getSmartFolder(smartFolderName);
			}
			bundle.remove(SELECTED_FOLDER_KEY);
			bundle.remove(SELECTED_ITEM_PATH_KEY);
			bundle.remove(SELECTED_SMART_FOLDER_KEY);
		}
	}

	@Nullable
	protected TrackItem geTrackItem(@NonNull TrackFolder folder, @NonNull String path) {
		for (TrackItem trackItem : folder.getFlattenedTrackItems()) {
			if (KAlgorithms.INSTANCE.stringsEqual(trackItem.getPath(), path)) {
				return trackItem;
			}
		}
		return null;
	}

	@Override
	public void onTrackItemsSelected(@NonNull Set<TrackItem> trackItems, boolean selected) {
		if (!trackItems.isEmpty()) {
			showTrackOnMap(trackItems.iterator().next());
		}
	}

	protected void reloadTracks() {
		reloadTracks(false);
	}

	protected void reloadTracks(boolean forceLoad) {
		TrackFoldersHelper foldersHelper = getTrackFoldersHelper();
		if (foldersHelper != null) {
			foldersHelper.reloadTracks(forceLoad);
		}
	}

	@Nullable
	public ScreenPositionData getFirstSuitableItemScreenPosition() {
		LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
		if (layoutManager != null) {
			int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
			int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
			for (int i = firstVisiblePosition; i <= lastVisiblePosition; i++) {
				Object item = adapter.getItemByPosition(firstVisiblePosition);
				if (item instanceof TrackItem || item instanceof TracksGroup) {
					ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(firstVisiblePosition);
					View view = viewHolder != null ? viewHolder.itemView : null;
					if (view != null) {
						return new ScreenPositionData(item, getViewOnScreenY(view));
					}
				}
			}
		}
		return null;
	}

	@Override
	public void onFolderRenamed(@NonNull File newDir) {
		updateContent();
	}

	@Override
	public void onFolderDeleted() {
		reloadTracks(true);
	}

	@Override
	public void showFolderTracksOnMap(@NonNull TrackFolder folder) {
		showTracksVisibilityDialog(folder.getId(), FOLDER, true);
	}

	@Override
	public void showSmartFolderTracksOnMap(@NonNull SmartFolder smartFolder) {
		showTracksVisibilityDialog(smartFolder.getId(), SMART_FOLDER, true);
	}

	protected void showTracksVisibilityDialog(@NonNull String id, @NonNull TrackTabType type, boolean selectAll) {
		FragmentActivity activity = getActivity();
		if (activity != null) {
			Bundle bundle = new Bundle();
			bundle.putString(PRESELECTED_TRACKS_TAB_ID, id);
			bundle.putBoolean(SELECT_ALL_ITEMS_ON_TAB, selectAll);
			bundle.putString(CALLING_FRAGMENT_TAG, TAG);
			MapActivity.launchMapActivityMoveToTop(activity, storeState(), null, bundle);
		}
	}

	@Override
	public void showExportDialog(@NonNull TrackFolder folder) {
		FragmentActivity activity = getActivity();
		if (activity != null) {
			TrackFoldersHelper foldersHelper = getTrackFoldersHelper();
			if (foldersHelper != null) {
				foldersHelper.showExportDialog(folder.getFlattenedTrackItems(), this);
			}
		}
	}

	@Override
	public void showExportDialog(@NonNull SmartFolder folder) {
		FragmentActivity activity = getActivity();
		if (activity != null) {
			TrackFoldersHelper foldersHelper = getTrackFoldersHelper();
			if (foldersHelper != null) {
				foldersHelper.showExportDialog(folder.getTrackItems(), this);
			}
		}
	}

	@Override
	public void showChangeAppearanceDialog(@NonNull TrackFolder folder) {
		FragmentActivity activity = getActivity();
		if (activity != null) {
			DefaultAppearanceController.showDialog(activity, folder);
		}
	}

	@Override
	public void showEditFiltersDialog(@NonNull SmartFolder folder, @Nullable DialogClosedListener dialogClosedListener) {
		FragmentManager manager = getFragmentManager();
		ArrayList<TrackItem> trackItems = new ArrayList<>(smartFolderHelper.getAllAvailableTrackItems());
		TracksSearchFilter filter = new TracksSearchFilter(app, trackItems, folder.getFilters());
		if (manager != null) {
			TracksFilterFragment.Companion.showInstance(app, manager, this, filter, dialogClosedListener, folder, null);
		}
	}

	@Override
	public void authorizationCompleted() {
		Bundle bundle = new Bundle();
		bundle.putInt(TAB_ID, GPX_TAB);

		Intent intent = new Intent(app, app.getAppCustomization().getMyPlacesActivity());
		intent.putExtra(MapActivity.INTENT_PARAMS, bundle);
		intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);

		app.startActivity(intent);
	}

	@Nullable
	protected MyPlacesActivity getMyActivity() {
		return (MyPlacesActivity) getActivity();
	}

	@NonNull
	protected MyPlacesActivity requireMyActivity() {
		return (MyPlacesActivity) requireActivity();
	}
}
