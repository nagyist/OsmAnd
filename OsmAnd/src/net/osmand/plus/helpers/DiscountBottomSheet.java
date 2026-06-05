package net.osmand.plus.helpers;

import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import net.osmand.plus.R;
import net.osmand.plus.Version;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment;
import net.osmand.plus.chooseplan.BasePurchaseDialogFragment.ButtonBackground;
import net.osmand.plus.chooseplan.MapsPlusPlanFragment;
import net.osmand.plus.chooseplan.OsmAndFeature;
import net.osmand.plus.chooseplan.OsmAndProPlanFragment;
import net.osmand.plus.chooseplan.button.PriceButton;
import net.osmand.plus.chooseplan.button.SubscriptionButton;
import net.osmand.plus.inapp.InAppPurchaseHelper;
import net.osmand.plus.inapp.InAppPurchaseUtils;
import net.osmand.plus.inapp.InAppPurchases.InAppPurchase;
import net.osmand.plus.inapp.InAppPurchases.InAppSubscription;
import net.osmand.plus.inapp.InAppPurchases.InAppSubscriptionIntroductoryInfo;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.widgets.FlowLayout;
import net.osmand.plus.widgets.TextViewEx;
import net.osmand.util.Algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiscountBottomSheet extends BaseMaterialBottomSheetDialogFragment {

	private static final String TAG = DiscountBottomSheet.class.getSimpleName();
	private static final String IN_APP_SKU_KEY = "in_app_sku_key";
	private static final String TITLE_KEY = "title";
	private static final String DESCRIPTION_KEY = "description";
	private static final String ICON_KEY = "icon";
	private static final String URL_KEY = "url";
	private static final String BUTTON_TITLE_KEY = "button_title";
	private static final String TITLE_COLOR_KEY = "title_color";
	private static final String DESCRIPTION_COLOR_KEY = "description_color";
	private static final String ICON_COLOR_KEY = "icon_color";
	private static final String BUTTON_TITLE_COLOR_KEY = "button_title_color";
	private static final String FEATURE_KEY = "feature";
	private static final String OSMAND_PRO_SKU = "osmand-pro";
	private static final String MAPS_PLUS_SKU = "osmand-maps-plus";
	private final List<PriceButton<?>> priceButtons = new ArrayList<>();
	private final Map<PriceButton<?>, View> buttonViews = new HashMap<>();
	@Nullable
	private PriceButton<?> selectedPriceButton;

	private static final List<BannerFeatureItem> MAPS_PLUS_BANNER_FEATURES = Arrays.asList(
			new BannerFeatureItem(OsmAndFeature.UNLIMITED_MAP_DOWNLOADS),
			new BannerFeatureItem(OsmAndFeature.ANDROID_AUTO),
			new BannerFeatureItem(OsmAndFeature.OSMAND_CLOUD),
			new BannerFeatureItem(OsmAndFeature.TERRAIN)
	);
	private static final List<BannerFeatureItem> OSMAND_PRO_BANNER_FEATURES = Arrays.asList(
			new BannerFeatureItem(OsmAndFeature.OSMAND_CLOUD),
			new BannerFeatureItem(OsmAndFeature.HOURLY_MAP_UPDATES),
			new BannerFeatureItem(OsmAndFeature.UNLIMITED_MAP_DOWNLOADS),
			new BannerFeatureItem(OsmAndFeature.VEHICLE_METRICS)
	);
	private static final List<BannerFeatureItem> OSMAND_PRO_WITH_MAPS_PLUS_BANNER_FEATURES = Arrays.asList(
			new BannerFeatureItem(OsmAndFeature.OSMAND_CLOUD),
			new BannerFeatureItem(OsmAndFeature.HOURLY_MAP_UPDATES),
			new BannerFeatureItem(OsmAndFeature.RELIEF_3D),
			new BannerFeatureItem(OsmAndFeature.VEHICLE_METRICS)
	);

	@Override
	public int getTheme() {
		return isNightMode() ? R.style.OsmandDarkTheme : R.style.OsmandLightTheme;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                         @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.bottom_sheet_discount_banner, container, false);
		Bundle args = requireArguments();
		setupBannerBackground(view.findViewById(R.id.banner_container));

		view.findViewById(R.id.close_button).setOnClickListener(v -> {
			requireMapActivity().getApp().logEvent("motd_close");
			dismiss();
		});
		setupRestoreButton(view.findViewById(R.id.restore_button));

		String featureName = args.getString(FEATURE_KEY);
		String inAppSku = args.getString(IN_APP_SKU_KEY);
		if (!Algorithms.isEmpty(featureName) || !Algorithms.isEmpty(inAppSku)) {
			OsmAndFeature feature = Algorithms.isEmpty(featureName) ? null : OsmAndFeature.valueOf(featureName);
			view.findViewById(R.id.plan_content).setVisibility(View.VISIBLE);
			view.findViewById(R.id.message_content).setVisibility(View.GONE);
			bindChoosePlanContent(view, feature, inAppSku, args);
		} else {
			view.findViewById(R.id.plan_content).setVisibility(View.GONE);
			view.findViewById(R.id.message_content).setVisibility(View.VISIBLE);
			bindMessageContent(view, args);
		}
		return view;
	}

	private void setupBannerBackground(@NonNull View bannerContainer) {
		int backgroundColor = AndroidUtils.getColorFromAttr(requireContext(), R.attr.list_background_color);
		float cornerRadius = getResources().getDimension(R.dimen.radius_double_large);
		Drawable contours = AppCompatResources.getDrawable(requireContext(), R.drawable.img_banner_contours);
		if (contours != null) {
			contours = contours.mutate();
			contours.setAlpha(isNightMode() ? 255 : 51);
		}
		AndroidUtils.setBackground(bannerContainer, new BannerBackgroundDrawable(backgroundColor, cornerRadius, contours));
	}

	private void bindMessageContent(@NonNull View view, @NonNull Bundle args) {
		View messageContent = view.findViewById(R.id.message_content);
		TextView title = messageContent.findViewById(R.id.title);
		title.setText(args.getString(TITLE_KEY));
		applyTextColor(title, args.getInt(TITLE_COLOR_KEY, -1));

		TextView description = messageContent.findViewById(R.id.description);
		String descriptionText = args.getString(DESCRIPTION_KEY);
		description.setText(descriptionText);
		description.setVisibility(Algorithms.isEmpty(descriptionText) ? View.GONE : View.VISIBLE);
		applyTextColor(description, args.getInt(DESCRIPTION_COLOR_KEY, -1));

		ImageView icon = messageContent.findViewById(R.id.icon);
		int iconId = getResources().getIdentifier(args.getString(ICON_KEY), "drawable", requireActivity().getPackageName());
		if (iconId != 0) {
			int iconColor = args.getInt(ICON_COLOR_KEY, -1);
			if (iconColor != -1) {
				icon.setImageDrawable(getApp().getUIUtilities().getPaintedIcon(iconId, iconColor));
			} else {
				icon.setImageResource(iconId);
			}
		} else {
			icon.setVisibility(View.GONE);
		}

		TextView actionButton = messageContent.findViewById(R.id.action_button);
		String buttonTitle = args.getString(BUTTON_TITLE_KEY);
		if (Algorithms.isEmpty(buttonTitle)) {
			buttonTitle = getString(R.string.shared_string_learn_more);
		}
		actionButton.setText(buttonTitle);
		applyTextColor(actionButton, args.getInt(BUTTON_TITLE_COLOR_KEY, -1));

		String url = args.getString(URL_KEY);
		View.OnClickListener clickListener = v -> {
			if (!Algorithms.isEmpty(url)) {
				MapActivity mapActivity = requireMapActivity();
				dismiss();
				DiscountHelper.onDiscountBottomSheetClicked(mapActivity, url);
			}
		};
		view.findViewById(R.id.banner_container).setOnClickListener(clickListener);
		actionButton.setOnClickListener(clickListener);
		actionButton.setVisibility(Algorithms.isEmpty(url) ? View.GONE : View.VISIBLE);
	}

	private void bindChoosePlanContent(@NonNull View view, @Nullable OsmAndFeature selectedFeature,
	                                   @Nullable String inAppSku, @NonNull Bundle args) {
		boolean nightMode = isNightMode();
		((ImageView) view.findViewById(R.id.header_icon)).setImageResource(getHeaderIconId(selectedFeature, inAppSku, nightMode));
		TextView headerIconTitle = view.findViewById(R.id.header_icon_title);
		String headerIconTitleText = selectedFeature != null
				? getString(selectedFeature.getTitleId()) : getHeaderIconTitle(inAppSku);
		headerIconTitle.setText(headerIconTitleText);
		headerIconTitle.setVisibility(Algorithms.isEmpty(headerIconTitleText) ? View.GONE : View.VISIBLE);
//		((TextView) view.findViewById(R.id.header_title)).setText(selectedFeature != null
//				? getString(selectedFeature.getTitleId())
//				: args.getString(TITLE_KEY));

//		int iconBgColor = AndroidUtils.getColorFromAttr(requireContext(), R.attr.purchase_sc_header_icon_bg);
//		AndroidUtils.setBackground(view.findViewById(R.id.header_icon_background),
//				createRoundedDrawable(iconBgColor, ButtonBackground.ROUNDED_LARGE));

		((TextView) view.findViewById(R.id.primary_description)).setText(selectedFeature != null
				? selectedFeature.getDescription(getApp())
				: args.getString(TITLE_KEY));

//		String mapsPlus = getString(R.string.maps_plus);
//		String osmAndPro = getString(R.string.osmand_pro);
//		TextView secondaryDescription = view.findViewById(R.id.secondary_description);
//		if (selectedFeature != null) {
//			String availablePlans = osmAndPro;
//			if (selectedFeature.isAvailableInMapsPlus()) {
//				availablePlans = getString(R.string.ltr_or_rtl_combine_via_or, mapsPlus, osmAndPro);
//			}
//			String secondaryDesc = String.format(getString(R.string.you_can_get_feature_as_part_of_pattern),
//					getString(selectedFeature.getTitleId()), availablePlans);
//			SpannableString message = UiUtilities.createSpannableString(secondaryDesc, Typeface.BOLD, mapsPlus, osmAndPro);
//			secondaryDescription.setText(message);
//			secondaryDescription.setVisibility(View.VISIBLE);
//		} else {
//			secondaryDescription.setVisibility(View.GONE);
//		}

		FlowLayout listContainer = view.findViewById(R.id.list_container);
		listContainer.removeAllViews();
		int spacing = getResources().getDimensionPixelSize(R.dimen.content_padding_half);
		for (BannerFeatureItem item : getBannerFeatureItems(inAppSku)) {
			View itemView = createFeatureChip(item);
			if (item.feature != null) {
				itemView.setOnClickListener(v -> bindChoosePlanContent(view, item.feature, inAppSku, args));
			}
			listContainer.addView(itemView, new FlowLayout.LayoutParams(spacing, spacing));
		}
		listContainer.addView(createLearnMoreChip(), new FlowLayout.LayoutParams(spacing, spacing));

		setupPriceButtons(view, selectedFeature, inAppSku);
	}

	private int getHeaderIconId(@Nullable OsmAndFeature selectedFeature, @Nullable String inAppSku, boolean nightMode) {
		if (selectedFeature != null) {
			return selectedFeature.getIconId(nightMode);
		}
		if (isOsmAndProSku(inAppSku)) {
			return R.drawable.ic_action_osmand_pro_logo_colored;
		} else if (isMapsPlusSku(inAppSku)) {
			return R.drawable.ic_action_osmand_maps_plus;
		}
		return R.drawable.ic_action_osmand_pro_logo_colored;
	}

	@Nullable
	private String getHeaderIconTitle(@Nullable String inAppSku) {
		if (isOsmAndProSku(inAppSku)) {
			return getString(R.string.osmand_pro);
		} else if (isMapsPlusSku(inAppSku)) {
			return getString(R.string.maps_plus);
		}
		return null;
	}

	private boolean isOsmAndProSku(@Nullable String inAppSku) {
		return OSMAND_PRO_SKU.equalsIgnoreCase(inAppSku);
	}

	private boolean isMapsPlusSku(@Nullable String inAppSku) {
		return MAPS_PLUS_SKU.equalsIgnoreCase(inAppSku);
	}

	@NonNull
	private List<BannerFeatureItem> getBannerFeatureItems(@Nullable String inAppSku) {
		if (isMapsPlusSku(inAppSku)) {
			return MAPS_PLUS_BANNER_FEATURES;
		} else if (InAppPurchaseUtils.isMapsPlusAvailable(getApp(), false) || InAppPurchaseUtils.isFullVersionAvailable(getApp(), false)) {
			return OSMAND_PRO_WITH_MAPS_PLUS_BANNER_FEATURES;
		}
		return OSMAND_PRO_BANNER_FEATURES;
	}

	private View createFeatureChip(@NonNull BannerFeatureItem item) {
		LinearLayout chip = new LinearLayout(requireContext());
		chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
		chip.setOrientation(LinearLayout.HORIZONTAL);
		chip.setMinimumHeight(dpToPx(36f));
		int horizontalPadding = dpToPx(12f);
		int verticalPadding = dpToPx(6f);
		chip.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
		chip.setBackground(createChipBackground());

		ImageView icon = new ImageView(requireContext());
		int iconSize = getResources().getDimensionPixelSize(R.dimen.standard_icon_size);
		LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
		iconParams.setMarginEnd(dpToPx(8f));
		icon.setImageResource(item.getIconId(isNightMode()));
		chip.addView(icon, iconParams);

		TextViewEx title = new TextViewEx(requireContext());
		title.setText(getString(item.titleId));
		title.setTextColor(AndroidUtils.getColorFromAttr(requireContext(), android.R.attr.textColorPrimary));
		title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
				getResources().getDimension(R.dimen.default_list_text_size));
		chip.addView(title, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return chip;
	}

	private View createLearnMoreChip() {
		TextViewEx chip = new TextViewEx(requireContext());
		chip.setGravity(android.view.Gravity.CENTER);
		chip.setMinimumHeight(dpToPx(36f));
		int horizontalPadding = dpToPx(12f);
		int verticalPadding = dpToPx(6f);
		chip.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
		chip.setText(R.string.shared_string_learn_more);
		chip.setTextColor(ColorUtilities.getActiveColor(getApp(), isNightMode()));
		chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
				getResources().getDimension(R.dimen.default_list_text_size));
		chip.setBackground(createChipBackground());
		chip.setOnClickListener(v -> {
			MapActivity mapActivity = requireMapActivity();
			dismiss();
			DiscountHelper.onDiscountBottomSheetClicked(mapActivity, requireArguments().getString(URL_KEY));
		});
		return chip;
	}

	private Drawable createChipBackground() {
		GradientDrawable drawable = new GradientDrawable();
		drawable.setColor(ColorUtilities.getActivityBgColor(getApp(), isNightMode()));
		drawable.setCornerRadius(getResources().getDimension(R.dimen.radius_small));
		return drawable;
	}

	private void setupRestoreButton(@NonNull View restoreButton) {
		int backgroundColor = ColorUtilities.getInactiveButtonsAndLinksColor(getApp(), isNightMode());
		Drawable normal = createRoundedDrawable(backgroundColor, ButtonBackground.ROUNDED);
		setupRoundedBackground(restoreButton, normal, ButtonBackground.ROUNDED);
		restoreButton.setOnClickListener(v -> {
			InAppPurchaseHelper purchaseHelper = getApp().getInAppPurchaseHelper();
			if (purchaseHelper != null) {
				purchaseHelper.requestInventory(true);
			}
		});
	}

	private void setupPriceButtons(@NonNull View view, @Nullable OsmAndFeature selectedFeature,
	                               @Nullable String inAppSku) {
		InAppPurchaseHelper purchaseHelper = getApp().getInAppPurchaseHelper();
		boolean nightMode = isNightMode();
		priceButtons.clear();
		buttonViews.clear();
		if (purchaseHelper == null) {
			return;
		}
		if (Version.isInAppPurchaseSupported()) {
			priceButtons.addAll(collectPriceButtons(purchaseHelper, selectedFeature, inAppSku, nightMode));
		}

		View purchaseButtons = view.findViewById(R.id.purchase_buttons);
		TextView cancelDescription = view.findViewById(R.id.cancel_description);
		boolean visible = !priceButtons.isEmpty();
		purchaseButtons.setVisibility(visible ? View.VISIBLE : View.GONE);
		cancelDescription.setVisibility(visible ? View.VISIBLE : View.GONE);
		if (!visible) {
			selectedPriceButton = null;
			updateDiscountBadge(view);
			return;
		}

		if (selectedPriceButton == null || !priceButtons.contains(selectedPriceButton)) {
			selectedPriceButton = priceButtons.get(0);
		}

		LinearLayout container = view.findViewById(R.id.price_block);
		container.removeAllViews();
		LayoutInflater inflater = LayoutInflater.from(requireContext());
		for (PriceButton<?> button : priceButtons) {
			View itemView = inflater.inflate(R.layout.purchase_dialog_btn_payment, container, false);
			TextView title = itemView.findViewById(R.id.title);
			TextView price = itemView.findViewById(R.id.price);
			TextView discount = itemView.findViewById(R.id.discount);
			TextView description = itemView.findViewById(R.id.description);

			title.setText(button.getTitle());
			price.setText(button.getPrice());
			description.setText(button.getDescription());
			discount.setText(button.getDiscount());

			description.setVisibility(Algorithms.isEmpty(button.getDescription()) ? View.GONE : View.VISIBLE);
			discount.setVisibility(Algorithms.isEmpty(button.getDiscount()) ? View.GONE : View.VISIBLE);

			int discountBgId = button.isDiscountApplied()
					? R.drawable.purchase_sc_discount_rectangle
					: R.drawable.purchase_save_discount_rectangle;
			AndroidUtils.setBackground(discount, getApp().getUIUtilities().getIcon(discountBgId));

			itemView.setOnClickListener(v -> {
				selectedPriceButton = button;
				updatePriceButtons(view);
			});

			buttonViews.put(button, itemView);
			container.addView(itemView);
		}

		setupApplyButton(view, purchaseHelper);
		setupCancelDescription(cancelDescription);
		updateDiscountBadge(view);
		updatePriceButtons(view);
	}

	private void updateDiscountBadge(@NonNull View view) {
		TextView primaryDescription = view.findViewById(R.id.primary_description);
		PriceButton<?> discountButton = getDiscountSubscriptionButton();
		String discount = discountButton != null ? discountButton.getDiscount() : null;
		if (!Algorithms.isEmpty(discount) && discountButton.isDiscountApplied()) {
			primaryDescription.setText(createDescriptionWithDiscount(primaryDescription.getText(), discount));
		}
	}

	@NonNull
	private CharSequence createDescriptionWithDiscount(@NonNull CharSequence description, @NonNull String discount) {
		SpannableStringBuilder builder = new SpannableStringBuilder(description);
		int start = builder.length();
		builder.append(" ").append(discount);
		int end = builder.length();
		builder.setSpan(new DiscountBadgeSpan(requireMapActivity()), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		return builder;
	}

	@Nullable
	private PriceButton<?> getDiscountSubscriptionButton() {
		for (PriceButton<?> button : priceButtons) {
			if (button instanceof SubscriptionButton && !Algorithms.isEmpty(button.getDiscount())) {
				return button;
			}
		}
		return null;
	}

	@NonNull
	private List<PriceButton<?>> collectPriceButtons(@NonNull InAppPurchaseHelper purchaseHelper,
	                                                 @Nullable OsmAndFeature selectedFeature, @Nullable String inAppSku, boolean nightMode) {
		if (isMapsPlusSku(inAppSku) && (selectedFeature == null || selectedFeature.isAvailableInMapsPlus())
				&& !isMapsPlusPurchased()) {
			return MapsPlusPlanFragment.collectPriceButtons(getApp(), purchaseHelper, nightMode);
		}
		return OsmAndProPlanFragment.collectPriceButtons(getApp(), purchaseHelper, nightMode);
	}

	private boolean isMapsPlusPurchased() {
		return !Version.isFreeVersion(getApp())
				|| InAppPurchaseUtils.isMapsPlusAvailable(getApp(), false)
				|| InAppPurchaseUtils.isFullVersionAvailable(getApp(), false);
	}

	private void updatePriceButtons(@NonNull View view) {
		boolean nightMode = isNightMode();
		int activeColor = ColorUtilities.getActiveColor(getApp(), nightMode);
		for (PriceButton<?> button : buttonViews.keySet()) {
			View itemView = buttonViews.get(button);
			if (itemView == null) {
				continue;
			}

			ImageView icon = itemView.findViewById(R.id.icon);
			Drawable normal;
			boolean selected = button.equals(selectedPriceButton);
			if (selected) {
				icon.setImageDrawable(getCheckmark());

				Drawable stroke = getActiveStrokeDrawable();
				int colorWithAlpha = ColorUtilities.getColorWithAlpha(activeColor, 0.1f);
				Drawable bgDrawable = getApp().getUIUtilities().getPaintedIcon(R.drawable.rectangle_rounded, colorWithAlpha);
				Drawable[] layers = {bgDrawable, stroke};
				normal = new LayerDrawable(layers);
			} else {
				icon.setImageDrawable(getEmptyCheckmark());
				normal = new ColorDrawable(Color.TRANSPARENT);
			}
			setupRoundedBackground(itemView, normal, ButtonBackground.ROUNDED);
			itemView.setContentDescription(getButtonContentDescription(button, selected));
		}
		updateApplyButton(view);
	}

	private void setupApplyButton(@NonNull View view, @NonNull InAppPurchaseHelper purchaseHelper) {
		View applyButton = view.findViewById(R.id.apply_button);
		applyButton.setOnClickListener(v -> {
			if (selectedPriceButton != null) {
				requireMapActivity().getApp().logEvent("motd_click");
				selectedPriceButton.onApply(requireActivity(), purchaseHelper);
			}
		});

		int activeColor = ColorUtilities.getActiveColor(getApp(), isNightMode());
		Drawable normal = createRoundedDrawable(activeColor, ButtonBackground.ROUNDED_SMALL);
		setupRoundedBackground(applyButton, normal, ButtonBackground.ROUNDED_SMALL);
	}

	private void updateApplyButton(@NonNull View view) {
		if (selectedPriceButton == null) {
			return;
		}
		View applyButton = view.findViewById(R.id.apply_button);
		TextView title = applyButton.findViewById(R.id.title);
		title.setText(getApplyButtonTitle(selectedPriceButton));

		TextView description = applyButton.findViewById(R.id.description);
		CharSequence price = getApplyButtonDescription(selectedPriceButton);
		if (price instanceof SpannableStringBuilder formattedPrice) {
			ForegroundColorSpan[] textColorSpans =
					formattedPrice.getSpans(0, formattedPrice.length(), ForegroundColorSpan.class);
			int textColor = title.getCurrentTextColor();
			if (textColorSpans.length > 0) {
				updateSpanColor(formattedPrice, textColorSpans[0], textColor);
			}
			if (textColorSpans.length > 1) {
				int semiTransparentTextColor = ColorUtilities.getColorWithAlpha(textColor, 0.5f);
				updateSpanColor(formattedPrice, textColorSpans[1], semiTransparentTextColor);
			}
		}
		description.setText(price);
	}

	@NonNull
	private CharSequence getApplyButtonTitle(@NonNull PriceButton<?> button) {
		InAppSubscriptionIntroductoryInfo introductoryInfo = getFreeTrialInfo(button);
		if (introductoryInfo != null) {
			return getString(R.string.start_free_trial);
		}
		return getString(R.string.complete_purchase);
	}

	@NonNull
	private CharSequence getApplyButtonDescription(@NonNull PriceButton<?> button) {
		InAppSubscriptionIntroductoryInfo introductoryInfo = getFreeTrialInfo(button);
		if (introductoryInfo != null) {
			return introductoryInfo.getRenewDescription(getApp());
		}
		return button.getPrice();
	}

	@Nullable
	private InAppSubscriptionIntroductoryInfo getFreeTrialInfo(@NonNull PriceButton<?> button) {
		InAppPurchase purchaseItem = button.getPurchaseItem();
		if (purchaseItem instanceof InAppSubscription subscription) {
			InAppSubscriptionIntroductoryInfo introductoryInfo = subscription.getIntroductoryInfo();
			if (introductoryInfo != null && introductoryInfo.isFreeTrial()) {
				return introductoryInfo;
			}
		}
		return null;
	}

	private void updateSpanColor(@NonNull SpannableStringBuilder spannable, @NonNull ForegroundColorSpan span,
	                             @ColorInt int color) {
		int start = spannable.getSpanStart(span);
		int end = spannable.getSpanEnd(span);
		spannable.removeSpan(span);
		spannable.setSpan(new ForegroundColorSpan(color), start, end, 0);
	}

	private void setupCancelDescription(@NonNull TextView cancelDescription) {
		if (Version.isHuawei()) {
			cancelDescription.setText(R.string.cancel_anytime_in_huawei_appgallery);
		} else if (Version.isAmazon()) {
			cancelDescription.setText(R.string.cancel_anytime_in_amazon_app);
		} else {
			cancelDescription.setText(R.string.cancel_anytime_in_gplay);
		}
	}

	private String getButtonContentDescription(@NonNull PriceButton<?> button, boolean selected) {
		StringBuilder builder = new StringBuilder(button.getTitle());
		String discount = button.getDiscount();
		if (!Algorithms.isEmpty(discount)) {
			builder.append(" ").append(discount);
		}
		builder.append(" ").append(button.getPrice());
		String description = button.getDescription();
		if (!Algorithms.isEmpty(description)) {
			builder.append(" ").append(description);
		}
		builder.append(" ").append(getString(selected ? R.string.shared_string_selected : R.string.shared_string_not_selected));
		return builder.toString();
	}

	private void setupRoundedBackground(@NonNull View view, @NonNull Drawable normal, @NonNull ButtonBackground background) {
		boolean nightMode = isNightMode();
		Drawable selected = AppCompatResources.getDrawable(requireContext(), background.getRippleId(nightMode));
		AndroidUtils.setBackground(view, UiUtilities.getLayeredIcon(normal, selected));
	}

	private Drawable createRoundedDrawable(int color, @NonNull ButtonBackground background) {
		return UiUtilities.createTintedDrawable(getApp(), background.drawableId, color);
	}

	@NonNull
	private Drawable getActiveStrokeDrawable() {
		return getApp().getUIUtilities().getIcon(isNightMode()
				? R.drawable.btn_background_stroked_active_dark
				: R.drawable.btn_background_stroked_active_light);
	}

	@NonNull
	private Drawable getCheckmark() {
		return getApp().getUIUtilities().getIcon(isNightMode()
				? R.drawable.ic_action_checkmark_colored_night
				: R.drawable.ic_action_checkmark_colored_day);
	}

	@NonNull
	private Drawable getEmptyCheckmark() {
		return getApp().getUIUtilities().getIcon(isNightMode()
				? R.drawable.ic_action_radio_button_night
				: R.drawable.ic_action_radio_button_day);
	}

	@Override
	public boolean isNightMode() {
		return getNightMode();
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(true);
		applyWindowDim(dialog.getWindow());
		dialog.setOnShowListener(dialogInterface -> applyDialogTransparency(dialog));
		return dialog;
	}

	@Override
	public void onStart() {
		super.onStart();
		Dialog dialog = getDialog();
		if (dialog != null) {
			applyWindowDim(dialog.getWindow());
			if (dialog instanceof BottomSheetDialog bottomSheetDialog) {
				applyDialogTransparency(bottomSheetDialog);
			}
		}
	}

	@Override
	public void onDismiss(@NonNull android.content.DialogInterface dialog) {
		super.onDismiss(dialog);
		DiscountHelper.onDiscountBottomSheetDismissed();
	}

	private void applyWindowDim(@Nullable Window window) {
		if (window != null) {
			window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
			window.setDimAmount(0.45f);
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		}
	}

	private void applyDialogTransparency(@NonNull BottomSheetDialog dialog) {
		View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
		if (bottomSheet != null) {
			bottomSheet.setBackgroundColor(Color.TRANSPARENT);
		}
	}

	private void applyTextColor(@NonNull TextView view, int color) {
		if (color != -1) {
			view.setTextColor(color);
		}
	}

	static boolean showInstance(@NonNull FragmentManager manager, @NonNull DiscountHelper.ControllerData data) {
		if (AndroidUtils.isFragmentCanBeAdded(manager, TAG)) {
			Bundle args = new Bundle();
			args.putString(TITLE_KEY, data.message);
			args.putString(IN_APP_SKU_KEY, data.inAppSku);
			args.putString(DESCRIPTION_KEY, data.description);
			args.putString(ICON_KEY, data.iconId);
			args.putString(URL_KEY, data.url);
			args.putString(BUTTON_TITLE_KEY, data.textBtnTitle);
			args.putInt(TITLE_COLOR_KEY, data.titleColor);
			args.putInt(DESCRIPTION_COLOR_KEY, data.descrColor);
			args.putInt(ICON_COLOR_KEY, data.iconColor);
			args.putInt(BUTTON_TITLE_COLOR_KEY, data.textBtnTitleColor);
			OsmAndFeature feature = data.getChoosePlanFeature();
			if (feature != null) {
				args.putString(FEATURE_KEY, feature.name());
			}
			DiscountBottomSheet fragment = new DiscountBottomSheet();
			fragment.setArguments(args);
			fragment.show(manager, TAG);
			return true;
		}
		return false;
	}

	private static class BannerFeatureItem {

		@StringRes
		final int titleId;
		@DrawableRes
		final int dayIconId;
		@DrawableRes
		final int nightIconId;
		@Nullable
		final OsmAndFeature feature;

		BannerFeatureItem(@NonNull OsmAndFeature feature) {
			this(feature.getListTitleId(), feature.getIconId(false), feature.getIconId(true), feature);
		}

		BannerFeatureItem(@StringRes int titleId, @DrawableRes int dayIconId, @DrawableRes int nightIconId,
		                  @Nullable OsmAndFeature feature) {
			this.titleId = titleId;
			this.dayIconId = dayIconId;
			this.nightIconId = nightIconId;
			this.feature = feature;
		}

		@DrawableRes
		int getIconId(boolean nightMode) {
			return nightMode ? nightIconId : dayIconId;
		}
	}

	private static class DiscountBadgeSpan extends ReplacementSpan {

		private final RectF rect = new RectF();
		private final int backgroundColor;
		private final int textColor;
		private final int paddingHorizontal;
		private final int paddingVertical;
		private final int marginStart;
		private final float cornerRadius;

		DiscountBadgeSpan(@NonNull MapActivity activity) {
			backgroundColor = ColorUtilities.getColor(activity, R.color.purchase_sc_discount);
			textColor = ColorUtilities.getColor(activity, R.color.active_buttons_and_links_text_light);
			paddingHorizontal = activity.getResources().getDimensionPixelSize(R.dimen.content_padding_small_half);
			paddingVertical = activity.getResources().getDimensionPixelSize(R.dimen.dash_margin);
			marginStart = activity.getResources().getDimensionPixelSize(R.dimen.content_padding_half);
			cornerRadius = activity.getResources().getDimension(R.dimen.radius_double_large);
		}

		@Override
		public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
		                   @Nullable Paint.FontMetricsInt fm) {
			if (fm != null) {
				Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
				fm.ascent = metrics.ascent - paddingVertical;
				fm.descent = metrics.descent + paddingVertical;
				fm.top = metrics.top - paddingVertical;
				fm.bottom = metrics.bottom + paddingVertical;
			}
			return Math.round(marginStart + paint.measureText(text, start, end) + 2 * paddingHorizontal);
		}

		@Override
		public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
		                 int top, int y, int bottom, @NonNull Paint paint) {
			int oldColor = paint.getColor();
			Paint.FontMetrics metrics = paint.getFontMetrics();
			float textWidth = paint.measureText(text, start, end);
			float badgeLeft = x + marginStart;
			float badgeRight = badgeLeft + textWidth + 2 * paddingHorizontal;
			float badgeTop = y + metrics.ascent - paddingVertical;
			float badgeBottom = y + metrics.descent + paddingVertical;

			paint.setColor(backgroundColor);
			rect.set(badgeLeft, badgeTop, badgeRight, badgeBottom);
			canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

			paint.setColor(textColor);
			canvas.drawText(text, start, end, badgeLeft + paddingHorizontal, y, paint);
			paint.setColor(oldColor);
		}
	}

	private static class BannerBackgroundDrawable extends Drawable {

		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Path path = new Path();
		private final RectF rect = new RectF();
		private final float cornerRadius;
		@Nullable
		private final Drawable contours;

		BannerBackgroundDrawable(int backgroundColor, float cornerRadius, @Nullable Drawable contours) {
			this.cornerRadius = cornerRadius;
			this.contours = contours;
			paint.setColor(backgroundColor);
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			Rect bounds = getBounds();
			rect.set(bounds);
			path.reset();
			path.addRoundRect(rect, new float[] {
					cornerRadius, cornerRadius,
					cornerRadius, cornerRadius,
					0, 0,
					0, 0
			}, Path.Direction.CW);

			int save = canvas.save();
			canvas.clipPath(path);
			canvas.drawPath(path, paint);
			if (contours != null) {
				int width = bounds.width();
				int intrinsicWidth = contours.getIntrinsicWidth();
				int intrinsicHeight = contours.getIntrinsicHeight();
				int height = intrinsicWidth > 0 && intrinsicHeight > 0
						? Math.round(width * intrinsicHeight / (float) intrinsicWidth)
						: bounds.height();
				contours.setBounds(bounds.left, bounds.top, bounds.right, bounds.top + height);
				contours.draw(canvas);
			}
			canvas.restoreToCount(save);
		}

		@Override
		public void setAlpha(int alpha) {
			paint.setAlpha(alpha);
		}

		@Override
		public void setColorFilter(@Nullable ColorFilter colorFilter) {
			paint.setColorFilter(colorFilter);
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}
	}
}
