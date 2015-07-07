package edu.nd.darts.cimon;

import android.content.Context;
import android.os.SystemClock;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.util.SparseArray;

import edu.nd.darts.cimon.database.CimonDatabaseAdapter;

/**
 * Monitoring service for Cell location
 *
 * @author ningxia
 */
public class CellLocationService extends MetricService<Integer> {

    private static final String TAG = "NDroid";
    private static final int CELL_METRICS = 2;
    private static final long THIRTY_SECONDS = 30000;

    private static final String title = "Cell location activity";
    private static final String[] metrics = {"GSM Cell ID", "GSM Location Area Code"};
    private static final int CELL_CID = Metrics.CELL_CID - Metrics.CELL_LOCATION_CATEGORY;
    private static final int CELL_LAC = Metrics.CELL_LAC - Metrics.CELL_LOCATION_CATEGORY;
    private static final CellLocationService INSTANCE = new CellLocationService();

    private TelephonyManager telephonyManager;
    private GsmCellLocation cellLocation;

    private CellLocationService() {
        if (DebugLog.DEBUG) Log.d(TAG, "CellLocationService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("CellLocationService already instantiated");
        }
        groupId = Metrics.CELL_LOCATION_CATEGORY;
        metricsCount = CELL_METRICS;

        Context context = MyApplication.getAppContext();
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        values = new Integer[CELL_METRICS];
        valueNodes = new SparseArray<ValueNode<Integer>>();
        freshnessThreshold = THIRTY_SECONDS;
        adminObserver = UserObserver.getInstance();
        adminObserver.registerObservable(this, groupId);
        schedules = new SparseArray<TimerNode>();
        init();
    }

    public static CellLocationService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "CellLocationService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void getMetricInfo() {
        if (DebugLog.DEBUG) Log.d(TAG, "CellLocationService.getMetricInfo - updating running applications");
        fetchValues();
        performUpdates();
    }

    @Override
    void insertDatabaseEntries() {
        if (DebugLog.DEBUG) Log.d(TAG, "CellLocationService.insertDatabaseEntries - insert entries");
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
        database.insertOrReplaceMetricInfo(groupId, title, "Cell Location", SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1 application", Metrics.TYPE_USER);
        for (int i = 0; i < CELL_METRICS; i ++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 1000);
        }
    }

    @Override
    Integer getMetricValue(int metric) {
        final long curTime = SystemClock.uptimeMillis();
        if ((curTime - lastUpdate) > freshnessThreshold) {
            fetchValues();
            lastUpdate = curTime;
        }
        if ((metric < groupId) || (metric >= (groupId + values.length))) {
            if (DebugLog.DEBUG) Log.d(TAG, "ApplicationService.getMetricValue - metric value" + metric + ", not valid for group" + groupId);
            return null;
        }
        return values[metric - groupId];
    }

    private void fetchValues() {
        cellLocation = (GsmCellLocation) telephonyManager.getCellLocation();
        if (cellLocation == null) {
            values[CELL_CID] = -1;
            values[CELL_LAC] = -1;
        }
        else {
            values[CELL_CID] = cellLocation.getCid() & 0xffff;
            values[CELL_LAC] = cellLocation.getLac() & 0xffff;
        }
    }
}
