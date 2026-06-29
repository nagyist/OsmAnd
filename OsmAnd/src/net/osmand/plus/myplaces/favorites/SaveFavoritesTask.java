package net.osmand.plus.myplaces.favorites;

import static net.osmand.IndexConstants.ZIP_EXT;
import static net.osmand.plus.myplaces.favorites.FavouritesHelper.getPointsFromGroups;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.data.FavouritePoint;
import net.osmand.plus.shared.SharedUtil;
import net.osmand.shared.gpx.GpxFile;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SaveFavoritesTask extends AsyncTask<Void, String, Void> {

	private static final Log log = PlatformUtil.getLog(SaveFavoritesTask.class);

	private final FavouritesFileHelper helper;
	private final List<FavoriteGroup> groups;
	@Nullable
	private final FavoritesListener listener;
	@Nullable
	private final CompletionListener completionListener;
	private final boolean saveAllGroups;

	public SaveFavoritesTask(@NonNull FavouritesFileHelper helper,
			@NonNull List<FavoriteGroup> groups, boolean saveAllGroups,
			@Nullable FavoritesListener listener) {
		this.saveAllGroups = saveAllGroups;
		this.helper = helper;
		this.groups = groups;
		this.listener = listener;
		this.completionListener = null;
	}

	private SaveFavoritesTask(@NonNull FavouritesFileHelper helper,
			@NonNull List<FavoriteGroup> groups, boolean saveAllGroups,
			@NonNull CompletionListener completionListener) {
		this.saveAllGroups = saveAllGroups;
		this.helper = helper;
		this.groups = groups;
		this.listener = null;
		this.completionListener = completionListener;
	}

	static SaveFavoritesTask createCoordinated(@NonNull FavouritesFileHelper helper,
			@NonNull List<FavoriteGroup> groups, boolean saveAllGroups,
			@NonNull CompletionListener completionListener) {
		return new SaveFavoritesTask(helper, groups, saveAllGroups, completionListener);
	}

	@Override
	protected Void doInBackground(Void... params) {
		boolean success;
		try {
			success = saveAllGroups
					? saveAllGroups(groups)
					: saveSelectedGroupsOnly(groups);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			success = false;
		}
		if (completionListener != null) {
			completionListener.onSaveFinished(success);
		}
		return null;
	}

	private boolean saveAllGroups(@NonNull List<FavoriteGroup> groups) {
		try {
			Map<String, FavoriteGroup> deletedGroups = new LinkedHashMap<>();
			Map<String, FavouritePoint> deletedPoints = new LinkedHashMap<>();

			File internalFile = helper.getInternalFile();
			GpxFile gpxFile = SharedUtil.loadGpxFile(internalFile);
			if (gpxFile.getError() == null) {
				helper.collectFavoriteGroups(gpxFile, deletedGroups);
			}
			// Get all points from internal file to filter later
			for (FavoriteGroup group : deletedGroups.values()) {
				for (FavouritePoint point : group.getPoints()) {
					deletedPoints.put(point.getKey(), point);
				}
			}
			// Hold only deleted points in map
			for (FavouritePoint point : getPointsFromGroups(groups)) {
				deletedPoints.remove(point.getKey());
			}
			// Hold only deleted groups in map
			for (FavoriteGroup group : groups) {
				deletedGroups.remove(group.getName());
			}
			// Save groups to internal file
			Exception internalError = helper.saveFileAtomic(groups, internalFile);
			if (internalError != null) {
				log.error(internalError.getMessage(), internalError);
				return false;
			}
			// Save groups to external files
			if (!saveExternalFiles(groups, deletedPoints.keySet())) {
				return false;
			}
			// Save groups to backup file
			// backup(groups, getBackupFile()); // creates new, but does not zip
			backup(helper.getBackupFile(), internalFile); // simply backs up internal file, hence internal name is reflected in gpx <name> metadata
			return true;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
	}

	private boolean saveSelectedGroupsOnly(@NonNull List<FavoriteGroup> groupsToSave) {
		try {
			// No need to touch internal file or backup
			// Changes will be picked up during next loadFavorites()
			for (FavoriteGroup group : groupsToSave) {
				if (!saveFavoriteGroup(group)) {
					return false;
				}
			}
			return true;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
	}

	private void loadGPXFiles(@NonNull Map<String, FavoriteGroup> favoriteGroups) {
		File[] files = helper.getFavoritesFiles();
		if (!Algorithms.isEmpty(files)) {
			for (File file : files) {
				GpxFile gpxFile = SharedUtil.loadGpxFile(file);
				if (gpxFile.getError() == null) {
					helper.collectFavoriteGroups(gpxFile, favoriteGroups);
				}
			}
		}
	}

	private boolean saveExternalFiles(@NonNull List<FavoriteGroup> localGroups,
			@NonNull Set<String> deleted) {
		Map<String, FavoriteGroup> fileGroups = new LinkedHashMap<>();
		loadGPXFiles(fileGroups);
		if (!saveLocalGroups(localGroups, fileGroups, deleted)) {
			return false;
		}
		cleanupOrphanedGroupFiles(localGroups, fileGroups);
		return true;
	}

	private void cleanupOrphanedGroupFiles(@NonNull List<FavoriteGroup> localGroups,
			@NonNull Map<String, FavoriteGroup> fileGroups) {
		for (FavoriteGroup fileGroup : fileGroups.values()) {
			// Search corresponding group in memory
			boolean hasLocalGroup = false;
			for (FavoriteGroup group : localGroups) {
				if (Algorithms.stringsEqual(group.getName(), fileGroup.getName())) {
					hasLocalGroup = true;
					break;
				}
			}
			// Delete external group file if it does not exist in local groups
			if (!hasLocalGroup) {
				helper.getExternalFile(fileGroup).delete();
			}
		}
	}

	private boolean saveLocalGroups(@NonNull List<FavoriteGroup> localGroups,
			@NonNull Map<String, FavoriteGroup> fileGroups, @NonNull Set<String> deleted) {
		for (FavoriteGroup localGroup : localGroups) {
			FavoriteGroup fileGroup = fileGroups.get(localGroup.getName());
			// Collect non deleted points from external group
			Map<String, FavouritePoint> all = new LinkedHashMap<>();
			if (fileGroup != null) {
				for (FavouritePoint point : fileGroup.getPoints()) {
					String key = point.getKey();
					if (!deleted.contains(key)) {
						all.put(key, point);
					}
				}
			}
			// Remove already existing in memory
			List<FavouritePoint> localPoints = new ArrayList<>(localGroup.getPoints());
			for (FavouritePoint point : localPoints) {
				all.remove(point.getKey());
			}
			// save favoritePoints from memory in order to update existing
			localGroup.getPoints().addAll(all.values());
			// Save file if group changed
			if (!localGroup.equals(fileGroup)) {
				if (!saveFavoriteGroup(localGroup)) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean saveFavoriteGroup(@NonNull FavoriteGroup group) {
		File externalFile = helper.getExternalFile(group);
		Exception exception = helper.saveFileAtomic(Collections.singletonList(group), externalFile);
		if (exception != null) {
			log.error(exception.getMessage(), exception);
			return false;
		} else if (externalFile.exists()) {
			group.setSize(externalFile.length());
			group.setTimeModified(externalFile.lastModified());
		}
		return true;
	}

	private void backup(@NonNull File backupFile, @NonNull File externalFile) {
		String name = backupFile.getName();
		String nameNoExt = name.substring(0, name.lastIndexOf(ZIP_EXT));
		InputStream fis = null;
		ZipOutputStream zos = null;
		try {
			File file = new File(backupFile.getParentFile(), backupFile.getName());
			zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
			fis = new BufferedInputStream(new FileInputStream(externalFile));
			zos.putNextEntry(new ZipEntry(nameNoExt));
			Algorithms.streamCopy(fis, zos);
			zos.closeEntry();
			zos.flush();
			zos.finish();
		} catch (Exception e) {
			log.warn("Backup failed", e);
		} finally {
			Algorithms.closeStream(zos);
			Algorithms.closeStream(fis);
		}
		helper.clearOldBackups();
	}

	interface CompletionListener {
		void onSaveFinished(boolean success);
	}

	@Override
	protected void onPostExecute(Void result) {
		if (listener != null) {
			listener.onSavingFavoritesFinished();
		}
	}
}
