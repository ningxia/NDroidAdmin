package edu.nd.darts.cimon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Set;

public class PhysicianReceiver extends BroadcastReceiver {

    private static final String TAG = "PhysicianReceiver";

    private static final String ACTION_START = "android.intent.action.BOOT_COMPLETED";
    private static final String ACTION_SHUTDOWN = "android.intent.action.ACTION_SHUTDOWN";


    private static final String PHYSICIAN_PREFS = "physician_prefs";
    private static final String RUNNING_METRICS = "running_metrics";
    private static final String EXTRA_NAME = "edu.nd.darts.cimon" + "." + RUNNING_METRICS;
    private static Set<String> runningMetrics;

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences physicianPrefs = context.getSharedPreferences(PHYSICIAN_PREFS, Context.MODE_PRIVATE);
        Intent i = new Intent(context, PhysicianService.class);
        if (ACTION_START.equals(intent.getAction())) {
            runningMetrics = physicianPrefs.getStringSet(RUNNING_METRICS, null);
            i.putStringArrayListExtra(EXTRA_NAME, new ArrayList<>(runningMetrics));
            context.startService(i);
            if (DebugLog.DEBUG) Log.d(TAG, "+ start PhysicianService +");
        }
        else if (ACTION_SHUTDOWN.equals(intent.getAction())) {
            context.stopService(i);
            if (DebugLog.DEBUG) Log.d(TAG, "+ stop PhysicianService +");
        }
    }
}
