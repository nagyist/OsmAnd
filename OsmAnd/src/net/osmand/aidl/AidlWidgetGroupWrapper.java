package net.osmand.aidl;

import net.osmand.aidlapi.mapwidget.AWidgetGroup;

public class AidlWidgetGroupWrapper {

	private final String id;
	private final String name;
	private final String description;
	private final String dayIconName;
	private final String nightIconName;

	public AidlWidgetGroupWrapper(AWidgetGroup group) {
		this.id = group.getId();
		this.name = group.getName();
		this.description = group.getDescription();
		this.dayIconName = group.getDayIconName();
		this.nightIconName = group.getNightIconName();
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getDayIconName() {
		return dayIconName;
	}

	public String getNightIconName() {
		return nightIconName;
	}
}
