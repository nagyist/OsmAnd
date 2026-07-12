package net.osmand.plus.search.dialogs

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import net.osmand.plus.R

class ChipsLayout @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

	enum class TextColorStyle {
		PRIMARY,
		SECONDARY,
		TERTIARY,
		PRIMARY_INVERSE,
		SECONDARY_INVERSE,
		TERTIARY_INVERSE
	}

	enum class IconColorStyle {
		DEFAULT,
		SECONDARY,
		PRIMARY,
		OSMAND,
		ACTIVE,
		WARNING
	}

	open class ChipData @JvmOverloads constructor(
		@JvmField val id: String,
		@DrawableRes @JvmField var iconId: Int,
		@JvmField var title: String?,
		@JvmField var selected: Boolean,
		@JvmField var visible: Boolean,
		@JvmField var enabled: Boolean,
		@JvmField var hasDropDown: Boolean,
		@JvmField var titleColor: TextColorStyle,
		@JvmField var iconColor: IconColorStyle,
		@StringRes @JvmField var menuTitleId: Int = 0,
		@JvmField var dropdownItems: List<DropdownItem> = emptyList(),
		@JvmField var showDropDownIconWhenDisabled: Boolean = false,
		@JvmField var onClickListener: OnChipClickListener? = null,
		@JvmField var onDropdownItemClickListener: OnDropdownItemClickListener? = null
	) {
		fun updateContent(chip: ChipData) {
			iconId = chip.iconId
			title = chip.title
			selected = chip.selected
			visible = chip.visible
			enabled = chip.enabled
			hasDropDown = chip.hasDropDown
			titleColor = chip.titleColor
			iconColor = chip.iconColor
			menuTitleId = chip.menuTitleId
			dropdownItems = chip.dropdownItems
			showDropDownIconWhenDisabled = chip.showDropDownIconWhenDisabled
			onClickListener = chip.onClickListener
			onDropdownItemClickListener = chip.onDropdownItemClickListener
		}
	}

	class DropDownChipData @JvmOverloads constructor(
		id: String,
		@DrawableRes iconId: Int,
		title: String?,
		selected: Boolean,
		visible: Boolean,
		enabled: Boolean,
		titleColor: TextColorStyle,
		iconColor: IconColorStyle,
		@StringRes menuTitleId: Int = 0,
		dropdownItems: List<DropdownItem> = emptyList(),
		showDropDownIconWhenDisabled: Boolean = false,
		onDropdownItemClickListener: OnDropdownItemClickListener? = null
	) : ChipData(
		id = id,
		iconId = iconId,
		title = title,
		selected = selected,
		visible = visible,
		enabled = enabled,
		hasDropDown = true,
		titleColor = titleColor,
		iconColor = iconColor,
		menuTitleId = menuTitleId,
		dropdownItems = dropdownItems,
		showDropDownIconWhenDisabled = showDropDownIconWhenDisabled,
		onDropdownItemClickListener = onDropdownItemClickListener
	)

	class DropdownItem @JvmOverloads constructor(
		@JvmField val id: Int,
		@DrawableRes @JvmField val iconId: Int,
		@JvmField val title: String,
		@JvmField val description: String? = null,
		@JvmField val selected: Boolean = false,
		@JvmField val enabled: Boolean = true,
		@JvmField val showDropDownIconWhenDisabled: Boolean = false,
		@JvmField val showDividerBelow: Boolean = false
	)

	fun interface OnChipClickListener {
		fun onChipClick(chipId: String)
	}

	fun interface OnDropdownItemClickListener {
		fun onDropdownItemClick(chipId: String, itemId: Int)
	}

	private var items by mutableStateOf<List<ChipData>>(emptyList(), neverEqualPolicy())
	private var expandedChipId by mutableStateOf<String?>(null)
	private var chipClickListener: OnChipClickListener? = null
	private var dropdownItemClickListener: OnDropdownItemClickListener? = null

	fun updateContent(chips: List<ChipData>) {
		val currentChips = items.associateBy { it.id }
		val updatedChips = chips.map { chip ->
			currentChips[chip.id]?.also { it.updateContent(chip) } ?: chip
		}
		items = updatedChips
		if (updatedChips.none { it.id == expandedChipId && it.visible && it.enabled && it.hasDropDown }) {
			expandedChipId = null
		}
	}

	fun setOnChipClickListener(listener: OnChipClickListener?) {
		chipClickListener = listener
	}

	fun setOnDropdownItemClickListener(listener: OnDropdownItemClickListener?) {
		dropdownItemClickListener = listener
	}

	@Composable
	override fun Content() {
		ChipsLayoutContent(
			items = items,
			expandedChipId = expandedChipId,
			onExpandedChipChanged = { expandedChipId = it },
			onChipClick = { chipClickListener?.onChipClick(it) },
			onDropdownItemClick = { chipId, itemId ->
				expandedChipId = null
				dropdownItemClickListener?.onDropdownItemClick(chipId, itemId)
			}
		)
	}
}

@Composable
private fun ChipsLayoutContent(
	items: List<ChipsLayout.ChipData>,
	expandedChipId: String?,
	onExpandedChipChanged: (String?) -> Unit,
	onChipClick: (String) -> Unit,
	onDropdownItemClick: (String, Int) -> Unit
) {
	val activityBackground = colorAttr(R.attr.activity_background_color)
	val listBackground = colorAttr(R.attr.list_background_color)
	val dividerColor = colorAttr(R.attr.divider_color_basic)
	val activeColor = colorAttr(R.attr.active_color_primary)
	val inActiveColor = colorAttr(R.attr.secondary_icon_color)
	val activeBackground = colorAttr(R.attr.active_color_secondary)
	val contentPadding = dimensionResource(R.dimen.content_padding)
	val halfPadding = dimensionResource(R.dimen.content_padding_half)
	val chips = items.filter { it.visible }

	MaterialTheme(
		colorScheme = lightColorScheme(
			primary = activeColor,
			surface = listBackground,
			background = activityBackground,
			onSurface = textColor(ChipsLayout.TextColorStyle.PRIMARY)
		)
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(36.dp)
				.horizontalScroll(rememberScrollState()),
			horizontalArrangement = Arrangement.spacedBy(halfPadding),
			verticalAlignment = Alignment.CenterVertically
		) {
			Spacer(modifier = Modifier.width(contentPadding - halfPadding))
			chips.forEach { chip ->
				ChipAnchor(
					chip = chip,
					expanded = expandedChipId == chip.id,
					changeExpandedState = { expanded ->
						onExpandedChipChanged(if (expanded) chip.id else null)
					},
					onChipClick = onChipClick,
					onDropdownItemClick = onDropdownItemClick,
					listBackground = listBackground,
					dividerColor = dividerColor,
					activeColor = activeColor,
					inActiveColor = inActiveColor,
					activeBackground = activeBackground
				)
			}
			Spacer(modifier = Modifier.width(contentPadding - halfPadding))
		}
	}
}

@Composable
private fun ChipAnchor(
	chip: ChipsLayout.ChipData,
	expanded: Boolean,
	changeExpandedState: (Boolean) -> Unit,
	onChipClick: (String) -> Unit,
	onDropdownItemClick: (String, Int) -> Unit,
	listBackground: Color,
	dividerColor: Color,
	activeColor: Color,
	inActiveColor: Color,
	activeBackground: Color
) {
	Box {
		val chipId = chip.id
		OsmandFilterChip(
			chipData = chip,
			selected = chip.selected || expanded,
			onClick = {
				if (chip.hasDropDown && chip.enabled) {
					changeExpandedState(true)
				} else if (chip.enabled) {
					val clickListener = chip.onClickListener
					if (clickListener != null) {
						clickListener.onChipClick(chipId)
					} else {
						onChipClick(chipId)
					}
				}
			},
			listBackground = listBackground,
			dividerColor = dividerColor,
			activeColor = activeColor,
			inActiveColor = inActiveColor,
			activeBackground = activeBackground
		)
		if (chip.hasDropDown) {
			DropdownMenu(
				expanded = expanded && chip.enabled,
				onDismissRequest = { changeExpandedState(false) },
				modifier = Modifier
					.background(listBackground)
					.padding(0.dp),
				offset = DpOffset(x = 0.dp, y = 4.dp)
			) {
				if (chip.menuTitleId != 0) {
					Text(
						text = stringResource(chip.menuTitleId),
						color = textColor(ChipsLayout.TextColorStyle.SECONDARY),
						fontSize = 16.sp,
						modifier = Modifier
							.fillMaxWidth()
							.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)
					)
				}
				chip.dropdownItems.forEach { item ->
					DropdownItemRow(
						item = item,
						onClick = {
							changeExpandedState(false)
							val dropdownItemClickListener = chip.onDropdownItemClickListener
							if (dropdownItemClickListener != null) {
								dropdownItemClickListener.onDropdownItemClick(chipId, item.id)
							} else {
								onDropdownItemClick(chipId, item.id)
							}
						},
						activeColor = activeColor,
						inActiveColor = inActiveColor
					)
					if (item.showDividerBelow) {
						Spacer(
							modifier = Modifier
								.fillMaxWidth()
								.height(1.dp)
								.background(dividerColor)
						)
					}
				}
			}
		}
	}
}

@Composable
private fun DropdownItemRow(
	item: ChipsLayout.DropdownItem,
	onClick: () -> Unit,
	activeColor: Color,
	inActiveColor: Color
) {
	val itemTextColor = textColor(ChipsLayout.TextColorStyle.PRIMARY)
	val secondaryTextColor = textColor(ChipsLayout.TextColorStyle.SECONDARY)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.wrapContentHeight()
			.clickable(enabled = item.enabled) { onClick() }
			.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		if (item.iconId != 0) {
			Icon(
				painter = painterResource(item.iconId),
				contentDescription = null,
				tint = iconColor(ChipsLayout.IconColorStyle.DEFAULT),
				modifier = Modifier.size(24.dp)
			)
		} else {
			RadioButton(
				selected = item.selected,
				onClick = null,
				enabled = item.enabled,
				colors = RadioButtonDefaults.colors(
					selectedColor = activeColor,
					unselectedColor = inActiveColor),
				modifier = Modifier.size(24.dp)
			)
		}
		Spacer(modifier = Modifier.width(20.dp))
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.Center
		) {
			Text(
				text = item.title,
				color = itemTextColor.copy(alpha = if (item.enabled) 1f else .5f),
				fontSize = 16.sp
			)
			if (item.description != null) {
				Text(
					text = item.description,
					color = secondaryTextColor.copy(alpha = if (item.enabled) 1f else .5f),
					fontSize = 14.sp
				)
			}
		}
	}
}

@Composable
private fun OsmandFilterChip(
	chipData: ChipsLayout.ChipData,
	selected: Boolean,
	onClick: () -> Unit,
	listBackground: Color,
	dividerColor: Color,
	activeColor: Color,
	inActiveColor: Color,
	activeBackground: Color
) {
	val labelColor = textColor(chipData.titleColor)
	val leadingIconColor = iconColor(chipData.iconColor)
	val trailingIconVisible =
		chipData.hasDropDown && (chipData.enabled || chipData.showDropDownIconWhenDisabled)
	val title = chipData.title
	val iconOnly = title == null && chipData.iconId != 0
	FilterChip(
		selected = selected,
		onClick = onClick,
		enabled = chipData.enabled,
		label = {
			if (iconOnly) {
				Icon(
					painter = painterResource(chipData.iconId),
					contentDescription = null,
					tint = leadingIconColor.copy(alpha = if (chipData.enabled) 1f else .5f),
					modifier = Modifier.size(18.dp)
				)
			} else if (title != null) {
				Text(
					text = title,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					fontSize = 14.sp,
					fontWeight = FontWeight.Medium
				)
			}
		},
		modifier = Modifier.height(36.dp),
		leadingIcon = if (chipData.iconId != 0 && !iconOnly) {
			{
				Icon(
					painter = painterResource(chipData.iconId),
					contentDescription = null,
					tint = leadingIconColor,
					modifier = Modifier.size(18.dp)
				)
			}
		} else {
			null
		},
		trailingIcon = if (trailingIconVisible) {
			{
				Icon(
					painter = painterResource(R.drawable.ic_action_arrow_drop_down),
					contentDescription = null,
					tint = labelColor,
					modifier = Modifier.size(24.dp)
				)
			}
		} else {
			null
		},
		shape = RoundedCornerShape(8.dp),
		colors = FilterChipDefaults.filterChipColors(
			containerColor = listBackground,
			labelColor = labelColor,
			iconColor = leadingIconColor,
			disabledContainerColor = listBackground,
			disabledLabelColor = labelColor.copy(alpha = .5f),
			disabledLeadingIconColor = leadingIconColor.copy(alpha = .5f),
			disabledTrailingIconColor = labelColor.copy(alpha = .5f),
			selectedContainerColor = activeBackground,
			selectedLabelColor = labelColor,
			selectedLeadingIconColor = leadingIconColor,
			selectedTrailingIconColor = labelColor
		),
		border = FilterChipDefaults.filterChipBorder(
			enabled = chipData.enabled,
			selected = selected,
			borderColor = dividerColor,
			selectedBorderColor = activeColor,
			disabledBorderColor = dividerColor,
			borderWidth = 1.dp,
			selectedBorderWidth = 1.dp
		)
	)
}

@Composable
private fun textColor(style: ChipsLayout.TextColorStyle): Color {
	return colorAttr(
		when (style) {
			ChipsLayout.TextColorStyle.PRIMARY -> android.R.attr.textColorPrimary
			ChipsLayout.TextColorStyle.SECONDARY -> android.R.attr.textColorSecondary
			ChipsLayout.TextColorStyle.TERTIARY -> android.R.attr.textColorTertiary
			ChipsLayout.TextColorStyle.PRIMARY_INVERSE -> android.R.attr.textColorPrimaryInverse
			ChipsLayout.TextColorStyle.SECONDARY_INVERSE -> android.R.attr.textColorSecondaryInverse
			ChipsLayout.TextColorStyle.TERTIARY_INVERSE -> android.R.attr.textColorTertiaryInverse
		}
	)
}

@Composable
private fun iconColor(style: ChipsLayout.IconColorStyle): Color {
	val context = LocalContext.current
	val configuration = LocalConfiguration.current
	val nightMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
			Configuration.UI_MODE_NIGHT_YES
	val colorId = when (style) {
		ChipsLayout.IconColorStyle.DEFAULT ->
			if (nightMode) R.color.icon_color_default_dark else R.color.icon_color_default_light

		ChipsLayout.IconColorStyle.SECONDARY ->
			if (nightMode) R.color.icon_color_secondary_dark else R.color.icon_color_secondary_light

		ChipsLayout.IconColorStyle.PRIMARY ->
			if (nightMode) R.color.icon_color_primary_dark else R.color.icon_color_primary_light

		ChipsLayout.IconColorStyle.OSMAND ->
			if (nightMode) R.color.icon_color_osmand_dark else R.color.icon_color_osmand_light

		ChipsLayout.IconColorStyle.ACTIVE ->
			if (nightMode) R.color.icon_color_active_dark else R.color.icon_color_active_light

		ChipsLayout.IconColorStyle.WARNING -> R.color.icon_color_warning
	}
	return Color(ContextCompat.getColor(context, colorId))
}

@Composable
private fun colorAttr(attrId: Int): Color {
	val context = LocalContext.current
	val typedValue = TypedValue()
	context.theme.resolveAttribute(attrId, typedValue, true)
	return Color(
		if (typedValue.resourceId != 0) {
			ContextCompat.getColor(context, typedValue.resourceId)
		} else {
			typedValue.data
		}
	)
}
