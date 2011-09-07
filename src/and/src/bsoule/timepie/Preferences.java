package bsoule.timepie;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class Preferences extends PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		ListPreference order = (ListPreference) findPreference("sortOrderPref");

		order.setSummary(order.getEntry());

		order.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				ListPreference x = (ListPreference) preference;
				CharSequence[] entries = x.getEntries();
				CharSequence[] entryValues = x.getEntryValues();
				CharSequence ourEntry = "";
				for (int i = 0; i < entryValues.length; i++) {
					if (newValue.equals(entryValues[i])) {
						ourEntry = entries[i];
					}
				}
				preference.setSummary(ourEntry);
				return true;
			}
		});
	}

}
