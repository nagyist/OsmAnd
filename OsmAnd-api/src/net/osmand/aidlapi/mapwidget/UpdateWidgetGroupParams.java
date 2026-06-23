package net.osmand.aidlapi.mapwidget;

import android.os.Bundle;
import android.os.Parcel;

import net.osmand.aidlapi.AidlParams;

public class UpdateWidgetGroupParams extends AidlParams {

	private AWidgetGroup group;

	public UpdateWidgetGroupParams(AWidgetGroup group) {
		this.group = group;
	}

	public UpdateWidgetGroupParams(Parcel in) {
		readFromParcel(in);
	}

	public static final Creator<UpdateWidgetGroupParams> CREATOR = new Creator<UpdateWidgetGroupParams>() {
		@Override
		public UpdateWidgetGroupParams createFromParcel(Parcel in) {
			return new UpdateWidgetGroupParams(in);
		}

		@Override
		public UpdateWidgetGroupParams[] newArray(int size) {
			return new UpdateWidgetGroupParams[size];
		}
	};

	public AWidgetGroup getGroup() {
		return group;
	}

	@Override
	public void writeToBundle(Bundle bundle) {
		bundle.putParcelable("group", group);
	}

	@Override
	protected void readFromBundle(Bundle bundle) {
		bundle.setClassLoader(AWidgetGroup.class.getClassLoader());
		group = bundle.getParcelable("group");
	}
}
