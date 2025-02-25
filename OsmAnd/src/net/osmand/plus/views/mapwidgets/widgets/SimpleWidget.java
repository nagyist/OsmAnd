package net.osmand.plus.views.mapwidgets.widgets;

import static net.osmand.plus.utils.AndroidUtils.dpToPx;

import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.preferences.CommonPreference;
import net.osmand.plus.settings.backend.preferences.OsmandPreference;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.views.layers.MapInfoLayer;
import net.osmand.plus.views.layers.base.OsmandMapLayer;
import net.osmand.plus.views.mapwidgets.WidgetType;
import net.osmand.plus.views.mapwidgets.WidgetsContextMenu;
import net.osmand.plus.views.mapwidgets.WidgetsPanel;
import net.osmand.plus.views.mapwidgets.widgetinterfaces.ISupportMultiRow;
import net.osmand.plus.views.mapwidgets.widgetinterfaces.ISupportWidgetResizing;
import net.osmand.plus.views.mapwidgets.widgetstates.SimpleWidgetState;
import net.osmand.plus.settings.enums.WidgetSize;
import net.osmand.plus.widgets.popup.PopUpMenuItem;
import net.osmand.util.Algorithms;

import java.util.List;

public abstract class SimpleWidget extends TextInfoWidget implements ISupportWidgetResizing, ISupportMultiRow {

	private final SimpleWidgetState widgetState;

	private TextView widgetNameTextView;
	private boolean verticalWidget;
	private boolean isFullRow;
	protected MapInfoLayer.TextState textState;
	@Nullable
	protected String customId;

	public SimpleWidget(@NonNull MapActivity mapActivity, @NonNull WidgetType widgetType, @Nullable String customId, @Nullable WidgetsPanel panel) {
		super(mapActivity, widgetType);
		this.customId = customId;

		WidgetsPanel selectedPanel = panel != null ? panel : widgetType.getPanel(customId != null ? customId : widgetType.id, settings);
		setVerticalWidget(selectedPanel);
		widgetState = new SimpleWidgetState(app, customId, widgetType, getDefaultWidgetSize());

		setupViews();
		updateWidgetView();
	}

	private void setupViews() {
		LinearLayout container = (LinearLayout) view;
		container.removeAllViews();

		int layoutId = getContentLayoutId();
		UiUtilities.getInflater(mapActivity, nightMode).inflate(layoutId, container);
		findViews();
		view.setOnLongClickListener(v -> {
			WidgetsContextMenu.showMenu(v, mapActivity, widgetType, customId, getWidgetActions(), verticalWidget, nightMode);
			return true;
		});
	}

	@LayoutRes
	protected int getContentLayoutId() {
		return verticalWidget ? getProperVerticalLayoutId(widgetState) : getProperSideLayoutId(widgetState);
	}

	protected WidgetSize getDefaultWidgetSize(){
		return isVerticalWidget() ? WidgetSize.MEDIUM : WidgetSize.SMALL;
	}

	public void updateValueAlign(boolean fullRow) {
		if (WidgetSize.SMALL != getWidgetSizePref().get()) {
			ViewGroup.LayoutParams textViewLayoutParams = textView.getLayoutParams();
			if (textViewLayoutParams instanceof FrameLayout.LayoutParams) {
				FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) textView.getLayoutParams();
				textView.setGravity(fullRow ? Gravity.CENTER : Gravity.START | Gravity.CENTER_VERTICAL);
				params.setMarginStart(dpToPx(app, (shouldShowIcon() || fullRow) ? 36 : 0));
				params.setMarginEnd(dpToPx(app, fullRow ? 36 : 0));
			}
		}
	}

	private void findViews() {
		container = view.findViewById(R.id.container);
		emptyBanner = view.findViewById(R.id.empty_banner);
		imageView = view.findViewById(R.id.widget_icon);
		textView = view.findViewById(R.id.widget_text);
		textViewShadow = view.findViewById(R.id.widget_text_shadow);
		smallTextViewShadow = view.findViewById(R.id.widget_text_small_shadow);
		smallTextView = view.findViewById(R.id.widget_text_small);
		widgetNameTextView = view.findViewById(R.id.widget_name);
		bottomDivider = view.findViewById(R.id.bottom_divider);
	}

	@Override
	protected int getLayoutId() {
		return R.layout.simple_widget_vertical_content_container;
	}

	@LayoutRes
	private int getProperSideLayoutId(@NonNull SimpleWidgetState simpleWidgetState) {
		return switch (simpleWidgetState.getWidgetSizePref().get()) {
			case SMALL -> R.layout.map_hud_widget;
			case LARGE -> R.layout.simple_map_widget_large;
			default -> R.layout.simple_map_widget_medium;
		};
	}

	@LayoutRes
	private int getProperVerticalLayoutId(@NonNull SimpleWidgetState simpleWidgetState) {
		return switch (simpleWidgetState.getWidgetSizePref().get()) {
			case SMALL ->
					isFullRow ? R.layout.simple_map_widget_small_full : R.layout.simple_map_widget_small;
			case LARGE -> R.layout.simple_map_widget_large;
			default -> R.layout.simple_map_widget_medium;
		};
	}

	public void setVerticalWidget(@NonNull WidgetsPanel panel) {
		verticalWidget = panel.isPanelVertical();
	}

	public boolean isVerticalWidget() {
		return verticalWidget;
	}

	public void updateWidgetView() {
		boolean showIcon = shouldShowIcon();
		AndroidUiHelper.updateVisibility(imageView, showIcon);
		updateWidgetName();
		if (verticalWidget) {
			app.getOsmandMap().getMapLayers().getMapInfoLayer().updateRow(this);
		}
	}

	public boolean shouldShowIcon() {
		boolean hideIcon = !verticalWidget && getWidgetSizePref().get() != WidgetSize.SMALL;
		return widgetState.getShowIconPref().get() && !hideIcon;
	}

	@NonNull
	public CommonPreference<Boolean> shouldShowIconPref() {
		return widgetState.getShowIconPref();
	}

	@Override
	public boolean allowResize() {
		return true;
	}

	@NonNull
	public OsmandPreference<WidgetSize> getWidgetSizePref() {
		return widgetState.getWidgetSizePref();
	}

	public void recreateViewIfNeeded(@NonNull WidgetsPanel panel) {
		boolean oldWidgetOrientation = verticalWidget;
		setVerticalWidget(panel);
		if (oldWidgetOrientation != verticalWidget) {
			recreateView();
		}
	}

	public void recreateView() {
		ImageView oldImageView = imageView;
		TextView oldTextView = textView;
		TextView oldTextViewShadow = textViewShadow;
		TextView oldSmallTextView = smallTextView;
		TextView oldSmallTextViewShadow = smallTextViewShadow;
		View oldContainer = container;
		View oldEmptyBanner = emptyBanner;
		View oldBottomDivider = bottomDivider;

		setupViews();
		findViews();

		imageView.setImageDrawable(oldImageView.getDrawable());
		copyView(imageView, oldImageView);
		view.setOnClickListener(getOnClickListener());
		view.setVisibility(oldContainer.getVisibility());

		copyTextView(textView, oldTextView);
		copyTextView(textViewShadow, oldTextViewShadow);
		copyTextView(smallTextView, oldSmallTextView);
		copyTextView(smallTextViewShadow, oldSmallTextViewShadow);
		copyView(emptyBanner, oldEmptyBanner);
		copyView(bottomDivider, oldBottomDivider);

		updateInfo(null);
		updateWidgetView();
	}

	@Nullable
	protected List<PopUpMenuItem> getWidgetActions() {
		return null;
	}

	@Override
	public final void updateInfo(@Nullable OsmandMapLayer.DrawSettings drawSettings) {
		boolean shouldHideTopWidgets = (verticalWidget && mapActivity.getWidgetsVisibilityHelper().shouldHideVerticalWidgets());
		boolean emptyValueTextView = Algorithms.isEmpty(textView.getText());
		boolean typeAllowed = widgetType != null && widgetType.isAllowed();
		boolean visible = typeAllowed && !(shouldHideTopWidgets || emptyValueTextView);

		updateVisibility(visible);
		if (typeAllowed && (!shouldHideTopWidgets || emptyValueTextView)) {
			updateSimpleWidgetInfo(drawSettings);
		}
	}

	protected void updateSimpleWidgetInfo(@Nullable OsmandMapLayer.DrawSettings drawSettings) {
	}

	@Override
	public boolean updateVisibility(boolean visible) {
		boolean updatedVisibility = super.updateVisibility(visible);
		if (verticalWidget && updatedVisibility) {
			app.getOsmandMap().getMapLayers().getMapInfoLayer().updateRow(this);
		}
		return updatedVisibility;
	}

	protected void updateWidgetName() {
		String widgetName = getWidgetName();
		if (widgetName != null && widgetNameTextView != null) {
			String additionalName = getAdditionalWidgetName();
			if (additionalName != null) {
				widgetName = widgetName + ", " + additionalName;
			}
			widgetNameTextView.setText(widgetName);
		}
	}

	@Nullable
	protected String getWidgetName() {
		return widgetType != null ? getString(widgetType.titleId) : null;
	}

	@Override
	public void copySettingsFromMode(@NonNull ApplicationMode sourceAppMode, @NonNull ApplicationMode appMode, @Nullable String customId) {
		if (widgetState != null) {
			widgetState.copyPrefsFromMode(sourceAppMode, appMode, customId);
		}
	}

	@Nullable
	protected String getAdditionalWidgetName() {
		return null;
	}

	private void copyTextView(@Nullable TextView newTextView, @Nullable TextView oldTextView) {
		if (newTextView != null && oldTextView != null) {
			newTextView.setTextColor(oldTextView.getCurrentTextColor());
			newTextView.setTypeface(oldTextView.getTypeface());
			newTextView.getPaint().setStrokeWidth(oldTextView.getPaint().getStrokeWidth());
			newTextView.getPaint().setStyle(oldTextView.getPaint().getStyle());
			newTextView.setText(oldTextView.getText());
			copyView(newTextView, oldTextView);
		}
	}

	private void copyView(@Nullable View newView, @Nullable View oldTView) {
		if (newView != null && oldTView != null) {
			newView.setFocusable(oldTView.isFocusable());
			newView.setVisibility(oldTView.getVisibility());
			newView.setContentDescription(oldTView.getContentDescription());
		}
	}

	protected View.OnClickListener getOnClickListener() {
		return null;
	}

	public void showIcon(boolean showIcon) {
		AndroidUiHelper.updateVisibility(imageView, showIcon);
		imageView.invalidate();
	}

	public void setImageDrawable(int res) {
		Drawable imageDrawable = iconsCache.getIcon(res, 0);
		if (shouldShowIcon()) {
			if (imageDrawable != null) {
				imageView.setImageDrawable(imageDrawable);
				Object anim = imageView.getDrawable();
				if (anim instanceof AnimationDrawable) {
					((AnimationDrawable) anim).start();
				}
				imageView.setVisibility(View.VISIBLE);
			}
		} else {
			imageView.setVisibility(View.GONE);
		}
		imageView.invalidate();
	}

	@Override
	public void updateColors(@NonNull MapInfoLayer.TextState textState) {
		this.textState = textState;
		if (verticalWidget || widgetState.getWidgetSizePref().get() != WidgetSize.SMALL) {
			updateVerticalWidgetColors(textState);
		} else {
			super.updateColors(textState);
		}
	}

	protected void updateVerticalWidgetColors(@NonNull MapInfoLayer.TextState textState) {
		nightMode = textState.night;
		textView.setTextColor(textState.textColor);
		smallTextView.setTextColor(textState.secondaryTextColor);
		widgetNameTextView.setTextColor(textState.secondaryTextColor);
		int iconId = getIconId();
		if (iconId != 0) {
			setImageDrawable(iconId);
		}
		view.findViewById(R.id.widget_bg).setBackgroundResource(textState.widgetBackgroundId);

		if (bottomDivider != null) {
			bottomDivider.setBackgroundResource(textState.widgetDividerColorId);
		}
	}

	@Override
	protected View getContentView() {
		return verticalWidget ? view : container;
	}

	public void updateFullRowState(boolean fullRow) {
		if (isFullRow != fullRow) {
			isFullRow = fullRow;
			recreateView();
			if (textState != null) {
				updateColors(textState);
			}
			updateInfo(null);
		}
	}
}
