package bsoule.timepie;

import java.util.Arrays;

import android.os.Bundle;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
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

		// Set the summary text of the order when it changes
		order.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				ListPreference x = (ListPreference) preference;
				int index = Arrays.asList(x.getEntryValues()).indexOf(newValue);
				preference.setSummary(x.getEntries()[index]);
				return true;
			}
		});

		CheckBoxPreference vibrate = (CheckBoxPreference) findPreference("pingVibrate");

		vibrate.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				CheckBoxPreference x = (CheckBoxPreference) preference;
				if (x.isChecked()) {
					Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
					v.vibrate(100);
				}
				return true;
			}
		});

	}

}
