package net.osmand.plus.search.dialogs

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import net.osmand.plus.widgets.popup.OsmAndDropdownMenu
import net.osmand.plus.widgets.popup.OsmAndDropdownMenuColors
import net.osmand.plus.widgets.popup.OsmAndDropdownMenuOption

class QuickSearchChipsToolbarView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

	class Option(
		@JvmField val id: Int,
		@StringRes @JvmField val titleId: Int,
		@JvmField val selected: Boolean
	)

	fun interface OnMenuOptionSelectedListener {
		fun onMenuOptionSelected(optionId: Int)
	}

	fun interface OnTypeChipClickListener {
		fun onTypeChipClick(typeName: String)
	}

	private var sortTitleId by mutableIntStateOf(R.string.shared_string_recent)

	private var sortIconId by mutableIntStateOf(R.drawable.ic_action_sort)

	private var sourceTitleId by mutableStateOf(R.string.shared_string_all)

	private var sourceIconId by mutableIntStateOf(R.drawable.ic_action_history)

	private var sortMenuOptions by mutableStateOf<List<Option>>(emptyList())
	private var sourceMenuOptions by mutableStateOf<List<Option>>(emptyList())
	private var typeChips by mutableStateOf<List<String>>(emptyList())
	private var selectedTypeChips by mutableStateOf<Set<String>>(emptySet())
	private var sortMenuExpanded by mutableStateOf(false)
	private var sourceMenuExpanded by mutableStateOf(false)
	private var sourceChipMenuEnabled by mutableStateOf(true)
	private var sortSelectedListener: OnMenuOptionSelectedListener? = null
	private var sourceSelectedListener: OnMenuOptionSelectedListener? = null
	private var typeChipClickListener: OnTypeChipClickListener? = null

	fun setSortChip(@StringRes titleId: Int, @DrawableRes iconId: Int) {
		sortTitleId = titleId
		sortIconId = iconId
	}

	fun setSourceChip(@StringRes titleId: Int, @DrawableRes iconId: Int) {
		sourceTitleId = titleId
		sourceIconId = iconId
	}

	fun setSortOptions(options: List<Option>) {
		sortMenuOptions = options.toList()
	}

	fun setSourceOptions(options: List<Option>) {
		sourceMenuOptions = options.toList()
	}

	fun setSourceMenuEnabled(enabled: Boolean) {
		sourceChipMenuEnabled = enabled
		if (!enabled) {
			sourceMenuExpanded = false
		}
	}

	fun setTypeChips(chips: List<String>, selectedChips: List<String>) {
		typeChips = chips.toList()
		selectedTypeChips = selectedChips.toSet()
	}

	fun setOnSortSelectedListener(listener: OnMenuOptionSelectedListener?) {
		sortSelectedListener = listener
	}

	fun setOnSourceSelectedListener(listener: OnMenuOptionSelectedListener?) {
		sourceSelectedListener = listener
	}

	fun setOnTypeChipClickListener(listener: OnTypeChipClickListener?) {
		typeChipClickListener = listener
	}

	@Composable
	override fun Content() {
		SearchChipsToolbar(
			sortTitleId = sortTitleId,
			sortIconId = sortIconId,
			sortOptions = sortMenuOptions,
			sortMenuExpanded = sortMenuExpanded,
			onSortMenuExpandedChange = { sortMenuExpanded = it },
			onSortSelected = {
				sortMenuExpanded = false
				sortSelectedListener?.onMenuOptionSelected(it)
			},
			sourceTitleId = sourceTitleId,
			sourceIconId = sourceIconId,
			sourceOptions = sourceMenuOptions,
			sourceMenuEnabled = sourceChipMenuEnabled,
			sourceMenuExpanded = sourceMenuExpanded,
			onSourceMenuExpandedChange = { sourceMenuExpanded = it },
			onSourceSelected = {
				sourceMenuExpanded = false
				sourceSelectedListener?.onMenuOptionSelected(it)
			},
			typeChips = typeChips,
			selectedTypeChips = selectedTypeChips,
			onTypeChipClick = { typeChipClickListener?.onTypeChipClick(it) }
		)
	}
}

@Composable
private fun SearchChipsToolbar(
	@StringRes sortTitleId: Int,
	@DrawableRes sortIconId: Int,
	sortOptions: List<QuickSearchChipsToolbarView.Option>,
	sortMenuExpanded: Boolean,
	onSortMenuExpandedChange: (Boolean) -> Unit,
	onSortSelected: (Int) -> Unit,
	@StringRes sourceTitleId: Int,
	@DrawableRes sourceIconId: Int,
	sourceOptions: List<QuickSearchChipsToolbarView.Option>,
	sourceMenuEnabled: Boolean,
	sourceMenuExpanded: Boolean,
	onSourceMenuExpandedChange: (Boolean) -> Unit,
	onSourceSelected: (Int) -> Unit,
	typeChips: List<String>,
	selectedTypeChips: Set<String>,
	onTypeChipClick: (String) -> Unit
) {
	val activityBackground = colorAttr(R.attr.activity_background_color)
	val listBackground = colorAttr(R.attr.list_background_color)
	val dividerColor = colorAttr(R.attr.divider_color_basic)
	val activeColor = colorAttr(R.attr.active_color_primary)
	val activeBackground = colorAttr(R.attr.active_color_secondary)
	val textColor = colorAttr(android.R.attr.textColorPrimary)
	val secondaryTextColor = colorAttr(android.R.attr.textColorSecondary)
	val disabledColor = colorAttr(android.R.attr.textColorTertiary)
	val toolbarPadding = dimensionResource(R.dimen.content_padding)
	val smallPadding = dimensionResource(R.dimen.content_padding_small)
	val halfPadding = dimensionResource(R.dimen.content_padding_half)

	MaterialTheme(
		colorScheme = lightColorScheme(
			primary = activeColor,
			surface = listBackground,
			background = activityBackground,
			onSurface = textColor
		)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.background(activityBackground)
		) {
			Spacer(
				modifier = Modifier
					.fillMaxWidth()
					.height(1.dp)
					.background(colorAttr(R.attr.ctx_menu_info_divider))
			)
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(
						start = toolbarPadding,
						top = smallPadding,
						end = toolbarPadding,
						bottom = smallPadding
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
					MenuChip(
						titleId = sortTitleId,
						iconId = sortIconId,
						selected = sortMenuExpanded,
						options = sortOptions,
						menuTitleId = R.string.sort_by,
						dividerAfterOptionId = null,
						menuExpanded = sortMenuExpanded,
						onExpandedChange = onSortMenuExpandedChange,
						onOptionSelected = onSortSelected,
						listBackground = listBackground,
						dividerColor = dividerColor,
						activeColor = activeColor,
						activeBackground = activeBackground,
						textColor = textColor,
						disabledColor = disabledColor,
						secondaryTextColor = secondaryTextColor
					)
					MenuChip(
						titleId = sourceTitleId,
						iconId = sourceIconId,
						selected = sourceMenuExpanded,
						options = sourceOptions,
						enabled = sourceMenuEnabled,
						menuTitleId = R.string.shared_string_type,
						dividerAfterOptionId = 0,
						menuExpanded = sourceMenuExpanded,
						onExpandedChange = onSourceMenuExpandedChange,
						onOptionSelected = onSourceSelected,
						listBackground = listBackground,
						dividerColor = dividerColor,
						activeColor = activeColor,
						activeBackground = activeBackground,
						textColor = textColor,
						disabledColor = disabledColor,
						secondaryTextColor = secondaryTextColor
					)
				}
				if (typeChips.isNotEmpty()) {
					Spacer(modifier = Modifier.height(smallPadding))
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.height(36.dp)
							.horizontalScroll(rememberScrollState()),
						horizontalArrangement = Arrangement.spacedBy(halfPadding),
						verticalAlignment = Alignment.CenterVertically
					) {
						typeChips.forEach { typeName ->
							HistoryFilterChip(
								title = typeName,
								selected = selectedTypeChips.contains(typeName),
								onClick = { onTypeChipClick(typeName) },
								listBackground = listBackground,
								dividerColor = dividerColor,
								activeColor = activeColor,
								activeBackground = activeBackground,
								textColor = textColor
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun MenuChip(
	@StringRes titleId: Int,
	@DrawableRes iconId: Int,
	selected: Boolean,
	options: List<QuickSearchChipsToolbarView.Option>,
	enabled: Boolean = true,
	@StringRes menuTitleId: Int?,
	dividerAfterOptionId: Int?,
	menuExpanded: Boolean,
	onExpandedChange: (Boolean) -> Unit,
	onOptionSelected: (Int) -> Unit,
	listBackground: Color,
	dividerColor: Color,
	activeColor: Color,
	activeBackground: Color,
	textColor: Color,
	disabledColor: Color,
	secondaryTextColor: Color
) {
	Box {
		HistoryFilterChip(
			title = stringResource(titleId),
			selected = selected,
			onClick = { onExpandedChange(true) },
			enabled = enabled,
			listBackground = listBackground,
			dividerColor = dividerColor,
			activeColor = activeColor,
			activeBackground = activeBackground,
			textColor = textColor,
			disabledColor = disabledColor,
			leadingIconId = iconId,
			trailingIconId = R.drawable.ic_action_arrow_drop_down
		)
		val menuOptions = options.map { option ->
			OsmAndDropdownMenuOption(
				value = option.id,
				title = stringResource(option.titleId),
				selected = option.selected,
				showDividerAfter = option.id == dividerAfterOptionId
			)
		}
		OsmAndDropdownMenu(
			expanded = enabled && menuExpanded,
			onDismissRequest = { onExpandedChange(false) },
			options = menuOptions,
			onOptionSelected = onOptionSelected,
			colors = OsmAndDropdownMenuColors(
				background = listBackground,
				divider = dividerColor,
				text = textColor,
				secondaryText = secondaryTextColor,
				icon = textColor,
				selected = activeColor
			),
			title = menuTitleId?.let { stringResource(it) }
		)
	}
}

@Composable
private fun HistoryFilterChip(
	title: String,
	selected: Boolean,
	onClick: () -> Unit,
	enabled: Boolean = true,
	listBackground: Color,
	dividerColor: Color,
	activeColor: Color,
	activeBackground: Color,
	textColor: Color,
	disabledColor: Color = textColor,
	@DrawableRes leadingIconId: Int? = null,
	@DrawableRes trailingIconId: Int? = null
) {
	val iconColor = if (enabled) activeColor else disabledColor
	val trailingIconColor = if (enabled) textColor else disabledColor
	FilterChip(
		selected = selected,
		onClick = onClick,
		enabled = enabled,
		label = {
			Text(
				text = title,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				fontSize = 14.sp,
				fontWeight = FontWeight.Medium
			)
		},
		modifier = Modifier.height(36.dp),
		leadingIcon = leadingIconId?.let {
			{
				Icon(
					painter = painterResource(it),
					contentDescription = null,
					tint = iconColor,
					modifier = Modifier.size(24.dp)
				)
			}
		},
		trailingIcon = trailingIconId?.let {
			{
				Icon(
					painter = painterResource(it),
					contentDescription = null,
					tint = trailingIconColor,
					modifier = Modifier.size(24.dp)
				)
			}
		},
		shape = RoundedCornerShape(8.dp),
		colors = FilterChipDefaults.filterChipColors(
			containerColor = listBackground,
			labelColor = textColor,
			iconColor = textColor,
			selectedContainerColor = activeBackground,
			selectedLabelColor = textColor,
			selectedLeadingIconColor = activeColor,
			selectedTrailingIconColor = textColor
		),
		border = FilterChipDefaults.filterChipBorder(
			enabled = true,
			selected = selected,
			borderColor = dividerColor,
			selectedBorderColor = activeColor,
			borderWidth = 1.dp,
			selectedBorderWidth = 1.dp
		)
	)
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
