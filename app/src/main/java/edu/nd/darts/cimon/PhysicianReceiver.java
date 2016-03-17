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
    private static final String MONITOR_STARTED = "monitor_started";
    private static boolean monitorStarted;

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences physicianPrefs = context.getSharedPreferences(PHYSICIAN_PREFS, Context.MODE_PRIVATE);
        Intent intentPhysician = new Intent(context, PhysicianService.class);
        Intent intentUpload = new Intent(context, UploadingService.class);
        Intent intentPing = new Intent(context, PingService.class);
        monitorStarted = physicianPrefs.getBoolean(MONITOR_STARTED, false);
        if (monitorStarted) {
            if (ACTION_START.equals(intent.getAction())) {
                context.startService(intentPhysician);
                context.startService(intentUpload);
                context.startService(intentPing);
                if (DebugLog.DEBUG) Log.d(TAG, "+ start PhysicianService +");
            }
            else if (ACTION_SHUTDOWN.equals(intent.getAction())) {
                context.stopService(intentPhysician);
                context.stopService(intentUpload);
                context.stopService(intentPing);
                if (DebugLog.DEBUG) Log.d(TAG, "+ stop PhysicianService +");
            }
        }
    }
}
