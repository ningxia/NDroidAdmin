package edu.nd.darts.cimon;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.provider.BaseColumns;
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
public class BluetoothService extends MetricService<String> {

    private static final String TAG = "NDroid";
    private static final int BLUETOOTH_METRICS = 1;
    private static final long FIVE_MINUTES = 300000;

    private static final String title = "Bluetooth activity";
    private static final String[] metrics = {"Discovered Bluetooth Devices"};
    private static final int BLUETOOTH_DEVICE = Metrics.BLUETOOTH_CATEGORY - Metrics.BLUETOOTH_DEVICE;
    private static final BluetoothService INSTANCE = new BluetoothService();

    private static BluetoothAdapter mBluetoothAdapter;
    private static List<BluetoothDevice> devices;

    private BluetoothService() {
        if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("BluetoothService already instantiated");
        }
        groupId = Metrics.BLUETOOTH_CATEGORY;
        metricsCount = BLUETOOTH_METRICS;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        devices = new ArrayList<>();

        values = new String[BLUETOOTH_METRICS];
        valueNodes = new SparseArray<ValueNode<String>>();
        freshnessThreshold = FIVE_MINUTES;
        adminObserver = UserObserver.getInstance();
        adminObserver.registerObservable(this, groupId);
        schedules = new SparseArray<TimerNode>();
        init();
    }

    public static BluetoothService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
        database.insertOrReplaceMetricInfo(groupId, title, mBluetoothAdapter.getName(), SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1 device", Metrics.TYPE_USER);
        database.insertOrReplaceMetrics(groupId + 0, groupId, metrics[0], "", 1000);
    }

    @Override
    void getMetricInfo() {
        if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService.getMetricInfo - updating Bluetooth device addresses");
        updateMetric = null;

        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        mBluetoothAdapter.startDiscovery();
        fetchValues();
        performUpdates();
    }

    public static BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                devices.add(device);
                Log.d(TAG, "BluetoothService.BluetoothReceiver - received device: " + device.getName() + "+" + device.getAddress());
            }
        }
    };

    @Override
    protected void performUpdates() {
        lastUpdate = SystemClock.uptimeMillis();
        long nextUpdate = updateValueNodes();
        scheduleNextUpdate(nextUpdate);
    }

    private void fetchValues() {
        if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService.fetchValues - updating Bluetooth values");
        if (mBluetoothAdapter == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < devices.size(); i ++) {
            sb.append(devices.get(i).getAddress())
                    .append(devices.size() - 1 == i ? "" : "|");
        }
        Log.d(TAG, "BluetoothService.fetchValues: " + sb.toString());
        // only record Bluetooth device address metric
        values[BLUETOOTH_DEVICE] = sb.toString();
        devices.clear();
    }

    @Override
    String getMetricValue(int metric) {
        final long curTime = SystemClock.uptimeMillis();
        if ((curTime - lastUpdate) > freshnessThreshold) {
            fetchValues();
            lastUpdate = curTime;
        }

        if ((metric < groupId) || (metric >= (groupId + values.length))) {
            if (DebugLog.DEBUG) Log.i(TAG, "BluetoothService.getMetricValue - metric value" + metric + ", not valid for group" + groupId);
            return null;
        }
        return values[metric - groupId];
    }

}
