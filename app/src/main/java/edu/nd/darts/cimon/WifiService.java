package edu.nd.darts.cimon;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

import edu.nd.darts.cimon.database.CimonDatabaseAdapter;

/**
 * Monitoring service for Bluetooth activity
 *
 * @author ningxia
 *
 * @see edu.nd.darts.cimon.MetricService
 */
public class WifiService extends MetricService<String> {

    private static final String TAG = "NDroid";
    private static final int WIFI_METRICS = 1;
    private static final long FIVE_MINUTES = 300000;

    private static final String title = "Wifi activity";
    private static final String[] metrics = {"Discovered Wifi Networks"};
    private static final int WIFI_NETWORK = Metrics.WIFI_CATEGORY - Metrics.WIFI_NETWORK;
    private static final WifiService INSTANCE = new WifiService();

    private WifiManager mWifiManager;

    private WifiService() {
        if (DebugLog.DEBUG) Log.d(TAG, "WifiService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("BluetoothService already instantiated");
        }
        groupId = Metrics.WIFI_CATEGORY;
        metricsCount = WIFI_METRICS;

        Context context = MyApplication.getAppContext();
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        values = new String[WIFI_METRICS];
        valueNodes = new SparseArray<ValueNode<String>>();
        freshnessThreshold = FIVE_MINUTES;
        adminObserver = UserObserver.getInstance();
        adminObserver.registerObservable(this, groupId);
        schedules = new SparseArray<TimerNode>();
        init();
    }

    public static WifiService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "WifiService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void getMetricInfo() {
        if (DebugLog.DEBUG) Log.d(TAG, "WifiService.getMetricInfo - updating Wifi network");
        fetchValues();
        performUpdates();
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
        database.insertOrReplaceMetricInfo(groupId, title, "WIFI", SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1 network", Metrics.TYPE_USER);
        database.insertOrReplaceMetrics(groupId + 0, groupId, metrics[0], "", 1000);
    }

    @Override
    String getMetricValue(int metric) {
        final long curTime = SystemClock.uptimeMillis();
        if ((curTime - lastUpdate) > freshnessThreshold) {
            fetchValues();
            lastUpdate = curTime;
        }
        if ((metric < groupId) || (metric >= (groupId + values.length))) {
            if (DebugLog.DEBUG) Log.i(TAG, "WifiService.getMetricValue - metric value" + metric + ", not valid for group" + groupId);
            return null;
        }
        return values[metric - groupId];
    }

    private void fetchValues() {
        if (DebugLog.DEBUG) Log.d(TAG, "WifiService.fetchValues - updating Wifi network values");
        if (mWifiManager == null) {
            return;
        }

        mWifiManager.startScan();
        List<ScanResult> scanResultList = new ArrayList<>(mWifiManager.getScanResults());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scanResultList.size(); i ++) {
//            if (i < scanResultList.size() - 1) {
//                sb.append(scanResultList.get(i).SSID).append("+").append(scanResultList.get(i).BSSID).append("|");
//            }
//            else {
//                sb.append(scanResultList.get(i).SSID).append("+").append(scanResultList.get(i).BSSID);
//            }
            sb.append(scanResultList.get(i).SSID)
                    .append("+")
                    .append(scanResultList.get(i).BSSID)
                    .append(scanResultList.size() - 1 == i ? "" : "|");
        }
        Log.d(TAG, "WifiService.fetchValues: " + sb.toString());
        // format: SSID+BSSID|SSID+BSSID|...
        values[WIFI_NETWORK] = sb.toString();
    }
}
