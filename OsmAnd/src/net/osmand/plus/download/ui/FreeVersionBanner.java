package net.osmand.plus.download.ui;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static net.osmand.plus.chooseplan.OsmAndFeature.UNLIMITED_MAP_DOWNLOADS;
import static net.osmand.plus.download.DownloadValidationManager.MAXIMUM_AVAILABLE_FREE_DOWNLOADS;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.chooseplan.ChoosePlanFragment;
import net.osmand.plus.chooseplan.button.PurchasingUtils;
import net.osmand.plus.chooseplan.button.SubscriptionButton;
import net.osmand.plus.download.DownloadActivity;
import net.osmand.plus.inapp.InAppPurchaseHelper;
import net.osmand.plus.inapp.InAppPurchases.InAppSubscription;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.settings.enums.ThemeUsageContext;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.util.Algorithms;

import java.util.List;

public class FreeVersionBanner {

	private static final int PROGRESS_SEGMENTS_COUNT = 6;

	private final OsmandApplication app;
	private final OsmandSettings settings;
	private final DownloadActivity activity;

	private final View freeVersionBanner;
	private final View freeVersionBannerTitle;
	private final View freeVersionCtaContainer;
	private final View freeVersionCtaContentContainer;
	private final TextView freeVersionTitleTextView;
	private final TextView freeVersionSubtitleTextView;
	private final TextView freeVersionDescriptionTextView;
	private final TextView downloadsLeftTextView;
	private final ImageView freeVersionCtaArrow;
	private final TextView freeVersionCtaDiscountBadge;

	private final OnClickListener onCollapseListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			app.logEvent("click_free_dialog");
			ChoosePlanFragment.showInstance(activity, UNLIMITED_MAP_DOWNLOADS);
		}
	};

	public FreeVersionBanner(@NonNull View view, @NonNull DownloadActivity activity) {
		this.activity = activity;
		this.app = activity.getApp();
		this.settings = app.getSettings();

		freeVersionBanner = view.findViewById(R.id.freeVersionBanner);
		freeVersionBannerTitle = freeVersionBanner.findViewById(R.id.freeVersionBannerTitle);
		freeVersionCtaContainer = freeVersionBanner.findViewById(R.id.freeVersionCtaContainer);
		freeVersionCtaContentContainer = freeVersionBanner.findViewById(R.id.freeVersionCtaContentContainer);
		freeVersionTitleTextView = freeVersionBanner.findViewById(R.id.freeVersionTitleTextView);
		freeVersionSubtitleTextView = freeVersionBanner.findViewById(R.id.freeVersionSubtitleTextView);
		downloadsLeftTextView = freeVersionBanner.findViewById(R.id.downloadsLeftTextView);
		freeVersionDescriptionTextView = freeVersionBanner.findViewById(R.id.freeVersionDescriptionTextView);
		freeVersionCtaArrow = freeVersionBanner.findViewById(R.id.freeVersionCtaArrow);
		freeVersionCtaDiscountBadge = freeVersionBanner.findViewById(R.id.freeVersionCtaDiscountBadge);
	}

	public void initFreeVersionBanner() {
		if (!DownloadActivity.shouldShowFreeVersionBanner(app)) {
			freeVersionBanner.setVisibility(View.GONE);
			return;
		}
		freeVersionBanner.setVisibility(View.VISIBLE);
		updateFreeVersionBanner();
	}

	public void updateFreeVersionBanner() {
		if (!DownloadActivity.shouldShowFreeVersionBanner(app)) {
			if (freeVersionBanner.getVisibility() == View.VISIBLE) {
				freeVersionBanner.setVisibility(View.GONE);
			}
			return;
		}
		setMinimizedFreeVersionBanner(false);
		int downloadsLeft = getDownloadsLeft(false);
		updateBannerState(downloadsLeft);
		updateDownloadsProgress(downloadsLeft);
		downloadsLeftTextView.setText(activity.getString(R.string.downloads_left_template, String.valueOf(downloadsLeft)));
		freeVersionBanner.findViewById(R.id.bannerTopLayout).setOnClickListener(onCollapseListener);
	}

	private void updateBannerState(int downloadsLeft) {
		boolean limitReached = downloadsLeft == 0;
		freeVersionTitleTextView.setText(limitReached ? R.string.free_download_limit_reached : R.string.free_version_title);
		downloadsLeftTextView.setVisibility(limitReached ? View.GONE : View.VISIBLE);
		freeVersionSubtitleTextView.setVisibility(limitReached ? View.VISIBLE : View.GONE);
		freeVersionDescriptionTextView.setText(R.string.get_unlimited_downloads);
		freeVersionCtaContainer.setOnClickListener(onCollapseListener);
		boolean nightMode = app.getDaynightHelper().isNightMode(settings.getApplicationMode(), ThemeUsageContext.APP);
		boolean emphasizedCta = limitReached || updateCtaDiscountBadge(nightMode);
		int ctaBackgroundId = R.drawable.free_version_banner_cta_neutral_ripple;
		if (emphasizedCta) {
			ctaBackgroundId = nightMode
					? R.drawable.free_version_banner_cta_bg_ripple_dark
					: R.drawable.free_version_banner_cta_bg_ripple;
		}
		freeVersionCtaContentContainer.setBackgroundResource(ctaBackgroundId);
		freeVersionCtaContainer.setVisibility(View.VISIBLE);
	}

	private boolean updateCtaDiscountBadge(boolean nightMode) {
		String discount = getSubscriptionIntroductoryDiscount();
		boolean hasDiscount = !Algorithms.isEmpty(discount);
		freeVersionCtaDiscountBadge.setText(discount);
		freeVersionCtaDiscountBadge.setBackgroundResource(nightMode
				? R.drawable.free_version_banner_cta_discount_bg_dark
				: R.drawable.free_version_banner_cta_discount_bg);
		freeVersionCtaDiscountBadge.setVisibility(hasDiscount ? View.VISIBLE : View.GONE);
		freeVersionCtaArrow.setVisibility(hasDiscount ? View.GONE : View.VISIBLE);
		return hasDiscount;
	}

	@Nullable
	private String getSubscriptionIntroductoryDiscount() {
		InAppPurchaseHelper purchaseHelper = app.getInAppPurchaseHelper();
		if (purchaseHelper == null) {
			return null;
		}
		boolean nightMode = app.getDaynightHelper().isNightMode(settings.getApplicationMode(), ThemeUsageContext.APP);
		List<InAppSubscription> subscriptions = purchaseHelper.getSubscriptions().getVisibleSubscriptions();
		List<SubscriptionButton> buttons = PurchasingUtils.collectSubscriptionButtons(app, purchaseHelper, subscriptions, nightMode);
		for (SubscriptionButton button : buttons) {
			InAppSubscription subscription = button.getPurchaseItem();
			if (button.isDiscountApplied()
					&& subscription.getIntroductoryInfo() != null
					&& !Algorithms.isEmpty(button.getDiscount())) {
				return button.getDiscount();
			}
		}
		return null;
	}

	private void updateDownloadsProgress(int downloadsLeft) {
		LinearLayout marksContainer = freeVersionBanner.findViewById(R.id.marksLinearLayout);
		marksContainer.removeAllViews();
		int markWidth = AndroidUtils.dpToPx(app, 2);
		boolean nightMode = app.getDaynightHelper().isNightMode(settings.getApplicationMode(), ThemeUsageContext.APP);
		int colorUsed = app.getColor(nightMode ? R.color.banner_downloads_used_dark : R.color.banner_downloads_used_light);
		int colorRemaining = app.getColor(nightMode ? R.color.banner_downloads_remaining_dark : R.color.banner_downloads_remaining_light);
		int usedSegments = PROGRESS_SEGMENTS_COUNT - Math.min(downloadsLeft, PROGRESS_SEGMENTS_COUNT);
		for (int i = 0; i < PROGRESS_SEGMENTS_COUNT; i++) {
			View markView = new View(activity);
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1);
			markView.setLayoutParams(params);
			markView.setBackground(getColoredTickDrawable(i < usedSegments ? colorUsed : colorRemaining));
			marksContainer.addView(markView);
			Space spaceView = new Space(activity);
			params = new LinearLayout.LayoutParams(markWidth, MATCH_PARENT);
			spaceView.setLayoutParams(params);
			marksContainer.addView(spaceView);
		}
	}

	private Drawable getColoredTickDrawable(int color) {
		Drawable drawable = activity.getDrawable(R.drawable.free_version_downloads_progress_tick);
		if (drawable != null) {
			drawable = drawable.mutate();
			drawable.setTint(color);
		}
		return drawable;
	}

	protected void setMinimizedFreeVersionBanner(boolean minimize) {
		if (minimize && DownloadActivity.isDownloadingPermitted(settings)) {
			freeVersionBannerTitle.setVisibility(View.GONE);
		} else {
			freeVersionBannerTitle.setVisibility(View.VISIBLE);
		}
	}

	protected void updateAvailableDownloads() {
		int downloadsLeft = getDownloadsLeft(true);
		updateBannerState(downloadsLeft);
		updateDownloadsProgress(downloadsLeft);
	}

	private int getDownloadsLeft(boolean includeActiveTasks) {
		int mapsDownloaded = settings.NUMBER_OF_FREE_DOWNLOADS.get();
		if (includeActiveTasks) {
			int activeTasks = activity.getDownloadThread().getCountedDownloads();
			mapsDownloaded += activeTasks;
		}
		int downloadsLeft = MAXIMUM_AVAILABLE_FREE_DOWNLOADS - mapsDownloaded;
		return Math.max(downloadsLeft, 0);
	}
}
