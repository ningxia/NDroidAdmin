package edu.nd.darts.cimon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import edu.nd.darts.cimon.database.CimonDatabaseAdapter;

/**
 * Monitoring service for Call State
 *
 * <li>Ringer mode: Normal</li>
 * <li>Ringer mode: Silent</li>
 * <li>Ringer mode: Vibrate</li>
 */
public class CallStateService extends MetricService<String> {
    private static final String TAG = "NDroid";
    private static final int CALLSTATE_METRICS = 1;
    private static final long FIVE_MINUTES = 300000;

    private static final String title = "Call state activity";
    private static final String[] metrics = {"Ringer Mode"};
    private static final int CALLSTATE = Metrics.CALLSTATE - Metrics.CALLSTATE_CATEGORY;
    private static final CallStateService INSTANCE = new CallStateService();
    private static final IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
    private Context context;
    private AudioManager mAudioManager;

    private CallStateService() {
        if (DebugLog.DEBUG) Log.d(TAG, "CallStateService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("CallStateService already instantiated");
        }
        groupId = Metrics.CALLSTATE_CATEGORY;
        metricsCount = CALLSTATE_METRICS;

        context = MyApplication.getAppContext();
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        values = new String[CALLSTATE_METRICS];
        valueNodes = new SparseArray<>();
        freshnessThreshold = FIVE_MINUTES;
        adminObserver = UserObserver.getInstance();
        adminObserver.registerObservable(this, groupId);
        schedules = new SparseArray<>();
        init();
    }

    public static CallStateService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void insertDatabaseEntries() {
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
        database.insertOrReplaceMetricInfo(groupId, title, "Ringer mode", SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1", Metrics.TYPE_USER);
        database.insertOrReplaceMetrics(groupId + 0, groupId, metrics[0], "", 1000);

    }

    @Override
    void getMetricInfo() {
        if (DebugLog.DEBUG) Log.d(TAG, "CallStateService.getMetricInfo - updating Call state");
        updateMetric = null;
        context.registerReceiver(callStateReceiver, filter);

        fetchValues();
        performUpdates();
    }

    private BroadcastReceiver callStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(intent.getAction())) {
                fetchValues();
            }
        }
    };

    @Override
    protected void performUpdates() {
        lastUpdate = SystemClock.uptimeMillis();
        long nextUpdate = updateValueNodes();
        if (nextUpdate < 0) {
            context.unregisterReceiver(callStateReceiver);
        }
        else {
            if (updateMetric == null) {
                scheduleNextUpdate(nextUpdate);
            }
        }
    }

    private void fetchValues() {
        if (DebugLog.DEBUG) Log.d(TAG, "CallStateService.fetchValues - updating Call State values");
        if (mAudioManager == null) {
            return;
        }

        switch (mAudioManager.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT:
                values[CALLSTATE] = "Silent";
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                values[CALLSTATE] = "Vibrate";
                break;
            case AudioManager.RINGER_MODE_NORMAL:
                values[CALLSTATE] = "Normal";
                break;
            default:
                values[CALLSTATE] = "Unknown";
                break;
        }
    }

    @Override
    String getMetricValue(int metric) {
        final long curTime = SystemClock.uptimeMillis();
        if ((curTime - lastUpdate) > freshnessThreshold) {
            fetchValues();
            lastUpdate = curTime;
        }

        if ((metric < groupId) || (metric >= (groupId + values.length))) {
            if (DebugLog.DEBUG) Log.d(TAG, "CallStateService.getMetricValue - metric value" + metric + ", not valid for group" + groupId);
            return null;
        }
        return values[metric - groupId];
    }

    @Override
    protected void updateObserver() {
        if (values[CALLSTATE].equals("Silent")) {
            adminObserver.setValue(Metrics.CALLSTATE, 0);
        }
        else if (values[CALLSTATE].equals("Vibrate")) {
            adminObserver.setValue(Metrics.CALLSTATE, 1);
        }
        else if (values[CALLSTATE].equals("Normal")) {
            adminObserver.setValue(Metrics.CALLSTATE, 2);
        }
        else { // Unknown
            adminObserver.setValue(Metrics.CALLSTATE, 3);
        }
    }
}
