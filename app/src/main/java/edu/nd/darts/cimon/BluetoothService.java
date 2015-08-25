package edu.nd.darts.cimon;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import java.util.HashMap;
import java.util.Map;

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
    private static final long BLE_SCAN_PERIOD = 10000;

    private static final String title = "Bluetooth activity";
    private static final String[] metrics = {"Discovered Bluetooth Devices"};
    private static final int BLUETOOTH_DEVICE = Metrics.BLUETOOTH_CATEGORY - Metrics.BLUETOOTH_DEVICE;
    private static final BluetoothService INSTANCE = new BluetoothService();

    private static BluetoothAdapter mBluetoothAdapter;
    private static HashMap<String, String> devices;
    private Handler mHandler;

    private BluetoothService() {
        if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("BluetoothService already instantiated");
        }
        groupId = Metrics.BLUETOOTH_CATEGORY;
        metricsCount = BLUETOOTH_METRICS;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        devices = new HashMap<>();

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
            active = false;
        }
        else {
            mHandler = new Handler(Looper.getMainLooper());
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, BLE_SCAN_PERIOD);
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
        fetchValues();
        performUpdates();
    }

//    public static BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
//                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                devices.add(device);
//                if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService.BluetoothReceiver - received device: " + device.getName() + "+" + device.getAddress());
//            }
//        }
//    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (!devices.containsKey(device.getAddress())) {
                devices.put(device.getAddress(), rssi + "+" + getUuid(scanRecord) + "+" + device.getAddress() + "+" + device.getName());
                if (DebugLog.DEBUG)
                    Log.d(TAG, "BluetoothService.LeScanCallback - received device: " + device.getName() + "+" + device.getAddress() + "+" + getUuid(scanRecord));
            }
        }
    };

    @Override
    protected void performUpdates() {
        lastUpdate = SystemClock.uptimeMillis();
        long nextUpdate = updateValueNodes();
        scheduleNextUpdate(nextUpdate);
        updateObservable();
    }

    private void fetchValues() {
        if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService.fetchValues - updating Bluetooth values");
        if (mBluetoothAdapter == null) {
            return;
        }
        if (devices.size() == 0) {
            values[BLUETOOTH_DEVICE] = "";
        }
        else {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry: devices.entrySet()) {
                sb.append(entry.getValue()).append("|");
            }
            if (DebugLog.DEBUG)
                Log.d(TAG, "BluetoothService.fetchValues: " + sb.substring(0, sb.length() - 1));
            values[BLUETOOTH_DEVICE] = sb.substring(0, sb.length() - 1);
            devices.clear();
        }
    }

    static final char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static String getUuid(byte[] scanRecord) {
        int startByte = 2;
        boolean patternFound = false;
        while (startByte <= 5) {
            if (    ((int) scanRecord[startByte + 2] & 0xff) == 0x02 && //Identifies an iBeacon
                    ((int) scanRecord[startByte + 3] & 0xff) == 0x15) { //Identifies correct data length
                patternFound = true;
                break;
            }
            startByte++;
        }
        //Convert to hex String
        byte[] uuidBytes = new byte[16];
        System.arraycopy(scanRecord, startByte+4, uuidBytes, 0, 16);
        String hexString = bytesToHex(uuidBytes);

        //Here is your UUID
        String uuid =  hexString.substring(0,8) + "-" +
                hexString.substring(8,12) + "-" +
                hexString.substring(12,16) + "-" +
                hexString.substring(16,20) + "-" +
                hexString.substring(20,32);

        //Here is your Major value
        int major = (scanRecord[startByte+20] & 0xff) * 0x100 + (scanRecord[startByte+21] & 0xff);

        //Here is your Minor value
        int minor = (scanRecord[startByte+22] & 0xff) * 0x100 + (scanRecord[startByte+23] & 0xff);

        return uuid;
    }

    @Override
    String getMetricValue(int metric) {
        final long curTime = SystemClock.uptimeMillis();
        if ((curTime - lastUpdate) > freshnessThreshold) {
            fetchValues();
            lastUpdate = curTime;
        }

        if ((metric < groupId) || (metric >= (groupId + values.length))) {
            if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService.getMetricValue - metric value" + metric + ", not valid for group" + groupId);
            return null;
        }
        return values[metric - groupId];
    }

    @Override
    protected void updateObserver() {
        adminObserver.setValue(Metrics.BLUETOOTH_DEVICE, values[BLUETOOTH_DEVICE].split("\\|").length);
    }
}
