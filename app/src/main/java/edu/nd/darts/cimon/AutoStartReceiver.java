/*
 * Copyright (C) 2013 Chris Miller
 *
 * This file is part of CIMON.
 * 
 * CIMON is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * CIMON is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *  
 * You should have received a copy of the GNU General Public License
 * along with CIMON.  If not, see <http://www.gnu.org/licenses/>.
 *  
 */
package edu.nd.darts.cimon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Receiver which starts CIMON monitoring service on boot.
 * User may disable this feature in options menu of administration app.
 * 
 * @author darts
 *
 */
public class AutoStartReceiver extends BroadcastReceiver {

	static final String ACTION = "android.intent.action.BOOT_COMPLETED";
	
    private static final String TAG = "AutoStartReceiver";
	private static final String SHARED_PREFS = "CimonSharedPrefs";
	private static final String PREF_STARTUP = "startup";

	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences appPrefs = context.getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
	    boolean startup = appPrefs.getBoolean(PREF_STARTUP, true);

        SharedPreferences physicianPrefs = context.getSharedPreferences(PhysicianInterface.PHYSICIAN_METRICS, Context.MODE_PRIVATE);
        Set<String> runningMonitorIds = new HashSet<>();
        Set<String> runningMetrics = physicianPrefs.getStringSet(PhysicianInterface.RUNNING_METRICS, null);
	    if (startup) {
			context.startService(new Intent(context, NDroidService.class));
			if (DebugLog.DEBUG) Log.d(TAG, "+ start CIMON Monitor +");
            if (runningMetrics != null) {
                for (String m : runningMetrics) {
                    int metric = Integer.parseInt(m);
                    int monitorId = PhysicianInterface.registerPeriodic(metric, PhysicianInterface.period, PhysicianInterface.duration);
                    runningMonitorIds.add(Integer.toString(monitorId));
                }
                SharedPreferences.Editor editor = physicianPrefs.edit();
                editor.putStringSet(PhysicianInterface.RUNNING_MONITOR_IDS, runningMonitorIds);
            }
	    }
	    else {
	    	if (DebugLog.DEBUG) Log.d(TAG, "+ CIMON Monitor - non-start on boot +");
	    }

	}

}
