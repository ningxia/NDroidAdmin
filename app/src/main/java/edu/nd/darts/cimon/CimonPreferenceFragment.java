package edu.nd.darts.cimon;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

/**
 * Preference Fragment for option menu
 *
 * @author ningxia
 */
public class CimonPreferenceFragment extends PreferenceFragment {

    private static final String PHONE_NUMBER = "phone_number";
    private EditTextPreference phoneNumberPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        phoneNumberPreference = (EditTextPreference) findPreference(PHONE_NUMBER);

        phoneNumberPreference.setSummary(phoneNumberPreference.getText());
        phoneNumberPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String duration = (String) newValue;
                phoneNumberPreference.setSummary(duration);
                return true;
            }
        });
    }
}
