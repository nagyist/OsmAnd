package net.osmand.plus.myplaces.tracks.dialogs;

import static androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import net.osmand.plus.R;
import net.osmand.plus.configmap.tracks.SearchTrackItemsFragment;
import net.osmand.plus.configmap.tracks.TrackItem;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.myplaces.tracks.TrackFoldersHelper;
import net.osmand.plus.track.data.TrackFolder;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.util.Algorithms;

public class TrackFolderFragment extends BaseTrackFolderFragment {

	public static final String TAG = TrackFolderFragment.class.getSimpleName();

	private TextView toolbarTitle;
	private ProgressBar progressBar;

	@Override
	protected int getLayoutId() {
		return R.layout.track_folder_fragment;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		FragmentActivity activity = requireActivity();
		activity.getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				onBackPressed();
			}
		});
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = super.onCreateView(inflater, container, savedInstanceState);
		if (view != null) {
			setupToolbar(view);
			setupProgressBar(view);
		}
		updateContent();
		return view;
	}

	private void setupProgressBar(@NonNull View view) {
		progressBar = view.findViewById(R.id.progress_bar);

		TrackFoldersHelper foldersHelper = getTrackFoldersHelper();
		boolean importing = foldersHelper != null && foldersHelper.isImporting();
		AndroidUiHelper.updateVisibility(progressBar, importing);
	}

	private void setupToolbar(@NonNull View view) {
		Toolbar toolbar = view.findViewById(R.id.toolbar);
		toolbarTitle = view.findViewById(R.id.toolbar_title);
		ViewCompat.setElevation(view.findViewById(R.id.appbar), 5.0f);

		ImageView closeButton = toolbar.findViewById(R.id.close_button);
		closeButton.setImageDrawable(getIcon(AndroidUtils.getNavigationIconResId(view.getContext())));
		closeButton.setOnClickListener(v -> onBackPressed());

		ViewGroup container = view.findViewById(R.id.actions_container);
		container.removeAllViews();

		LayoutInflater inflater = UiUtilities.getInflater(view.getContext(), nightMode);
		setupSearchButton(inflater, container);
		setupMenuButton(inflater, container);
	}

	private void setupSearchButton(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
		ImageButton button = (ImageButton) inflater.inflate(R.layout.action_button, container, false);
		button.setImageDrawable(getIcon(R.drawable.ic_action_search_dark));
		button.setOnClickListener(v -> {
			FragmentActivity activity = getActivity();
			if (activity != null) {
				SearchTrackItemsFragment.showInstance(activity.getSupportFragmentManager(), getTargetFragment());
			}
		});
		button.setContentDescription(getString(R.string.shared_string_search));
		container.addView(button);
	}

	private void setupMenuButton(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
		ImageButton button = (ImageButton) inflater.inflate(R.layout.action_button, container, false);
		button.setImageDrawable(getIcon(R.drawable.ic_overflow_menu_white));
		button.setOnClickListener(v -> {
			TrackFoldersHelper foldersHelper = getTrackFoldersHelper();
			if (foldersHelper != null) {
				foldersHelper.showFolderOptionsMenu(v, selectedFolder, this);
			}
		});
		button.setContentDescription(getString(R.string.shared_string_more));
		container.addView(button);
	}

	private void onBackPressed() {
		if (rootFolder.equals(selectedFolder)) {
			dismiss();
		} else {
			selectedFolder = selectedFolder.getParentFolder();
			updateContent();
		}
	}

	private void dismiss() {
		FragmentActivity activity = getActivity();
		if (activity != null) {
			activity.getSupportFragmentManager().popBackStack(TAG, POP_BACK_STACK_INCLUSIVE);
		}
	}

	@Override
	public void updateContent() {
		super.updateContent();
		toolbarTitle.setText(selectedFolder.getName(app));
	}

	@Override
	public void onResume() {
		super.onResume();
		updateActionBar(false);
		restoreState(getArguments());
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		updateActionBar(true);
	}

	@Override
	public void onImportStarted() {
		AndroidUiHelper.updateVisibility(progressBar, true);
	}

	@Override
	public void onImportFinished() {
		AndroidUiHelper.updateVisibility(progressBar, false);
	}

	@Override
	public void onTrackItemOptionsSelected(@NonNull View view, @NonNull TrackItem trackItem) {
		TrackFoldersHelper foldersHelper = getTrackFoldersHelper();
		if (foldersHelper != null) {
			foldersHelper.showItemOptionsMenu(view, trackItem, this);
		}
	}

	@Override
	public void restoreState(Bundle bundle) {
		super.restoreState(bundle);

		if (!Algorithms.isEmpty(selectedItemPath)) {
			TrackItem trackItem = geTrackItem(rootFolder, selectedItemPath);
			if (trackItem != null) {
				int index = adapter.getItemPosition(trackItem);
				if (index != -1) {
					recyclerView.scrollToPosition(index);
				}
			}
			selectedItemPath = null;
		}
	}

	public static void showInstance(@NonNull FragmentManager manager, @NonNull TrackFolder folder, @Nullable Fragment target) {
		if (AndroidUtils.isFragmentCanBeAdded(manager, TAG)) {
			TrackFolderFragment fragment = new TrackFolderFragment();
			fragment.setRootFolder(folder);
			fragment.setSelectedFolder(folder);
			fragment.setTargetFragment(target, 0);
			fragment.setRetainInstance(true);

			manager.beginTransaction()
					.replace(R.id.fragmentContainer, fragment, TAG)
					.addToBackStack(TAG)
					.commitAllowingStateLoss();
		}
	}
}