package net.osmand.plus.media;

import androidx.annotation.NonNull;

import net.osmand.util.MapUtils;

import java.io.File;

public class MediaFileUtils {

	public static final String IMG_EXTENSION = "jpg";
	public static final String MPEG4_EXTENSION = "mp4";
	public static final String THREEGP_EXTENSION = "3gp";

	@NonNull
	public static File getBaseFileName(double lat, double lon, @NonNull File dir, @NonNull String ext) {
		String basename = MapUtils.createShortLinkString(lat, lon, 15);
		int index = 1;
		dir.mkdirs();
		File file;
		do {
			file = new File(dir, basename + "." + (index++) + "." + ext);
		} while (file.exists());
		return file;
	}
}