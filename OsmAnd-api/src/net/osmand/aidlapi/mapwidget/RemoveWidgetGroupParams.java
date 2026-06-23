package net.osmand.aidlapi.mapwidget;

import android.os.Bundle;
import android.os.Parcel;

import net.osmand.aidlapi.AidlParams;

public class RemoveWidgetGroupParams extends AidlParams {

	private String id;

	public RemoveWidgetGroupParams(String id) {
		this.id = id;
	}

	public RemoveWidgetGroupParams(Parcel in) {
		readFromParcel(in);
	}

	public static final Creator<RemoveWidgetGroupParams> CREATOR = new Creator<RemoveWidgetGroupParams>() {
		@Override
		public RemoveWidgetGroupParams createFromParcel(Parcel in) {
			return new RemoveWidgetGroupParams(in);
		}

		@Override
		public RemoveWidgetGroupParams[] newArray(int size) {
			return new RemoveWidgetGroupParams[size];
		}
	};

	public String getId() {
		return id;
	}

	@Override
	public void writeToBundle(Bundle bundle) {
		bundle.putString("id", id);
	}

	@Override
	protected void readFromBundle(Bundle bundle) {
		id = bundle.getString("id");
	}
}
