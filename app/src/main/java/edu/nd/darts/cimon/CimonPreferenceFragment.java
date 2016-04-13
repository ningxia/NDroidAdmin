package edu.nd.darts.cimon;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;

import com.github.mikephil.charting.utils.Utils;

/**
 * Preference Fragment for option menu
 *
 * @author ningxia
 */
public class CimonPreferenceFragment extends PreferenceFragment {

    private static final String TAG = "NDroid";
    private static final String SHARED_PREFS = "CimonSharedPrefs";
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
                String phoneNumber = (String) newValue;
                phoneNumber = phoneNumber.replaceAll("[\\D]", "");
                SharedPreferences appPrefs = MyApplication.getAppContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
                appPrefs.edit().putString(PHONE_NUMBER, phoneNumber).commit();
                Log.d(TAG, "Phone number updated: " + phoneNumber);
                phoneNumberPreference.setSummary((String) newValue);
                return true;
            }
        });
    }
}
