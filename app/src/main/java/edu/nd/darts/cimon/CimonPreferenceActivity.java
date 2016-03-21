package edu.nd.darts.cimon;

import android.app.Activity;
import android.os.Bundle;

/**
 * Cimon Preference Activity
 *
 * @author ningxia
 */
public class CimonPreferenceActivity extends Activity {

    public static final int RESULT_CODE = 2001;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new CimonPreferenceFragment())
                .commit();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setResult(RESULT_CODE);
    }
}
