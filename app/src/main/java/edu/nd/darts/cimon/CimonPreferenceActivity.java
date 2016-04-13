package edu.nd.darts.cimon;

import android.app.Activity;
import android.os.Bundle;

/**
 * Cimon Preference Activity
 *
 * @author ningxia
 */
public class CimonPreferenceActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new CimonPreferenceFragment())
                .commit();
    }
}
