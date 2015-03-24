package edu.nd.darts.cimon;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PhysicianService extends Service {

    private static final String TAG = "NDroid";
    private static final String THREADTAG = "PhysicianServiceThread";
    private static final String PACKAGE_NAME = "edu.nd.darts.cimon";

    private static CimonInterface mCimonInterface = null;

    private static boolean serviceConnected = false;

    BluetoothAdapter mBluetoothAdapter;

    private static final String PHYSICIAN_PREFS = "physician_prefs";
    private static final String RUNNING_METRICS = "running_metrics";
    private static final String RUNNING_MONITOR_IDS = "running_monitor_ids";
    private static SharedPreferences settings;
    private static SharedPreferences.Editor editor;
    private static Set<String> runningMetrics;
    private static Set<String> runningMonitorIds;

    private static final HandlerThread thread = new HandlerThread(THREADTAG) {
        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DebugLog.DEBUG) Log.d(TAG, "PhysicianService.onStartCommand  - started - serviceConnected: " + serviceConnected);
        runningMetrics = new HashSet<>(intent.getStringArrayListExtra(PACKAGE_NAME + "." + RUNNING_METRICS));
        if (!serviceConnected) {
            if (DebugLog.DEBUG) Log.d(TAG, "PhysicianService.onCreate - start binding Cimon");
            Intent i = new Intent(this, NDroidService.class);
            bindService(i, mConnection, Context.BIND_AUTO_CREATE);
        }
        else {
            mCimonInterface = getCimonInterface();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        if (DebugLog.DEBUG) Log.d(TAG, "PhysicianService.onCreate - created");
        super.onCreate();
        if (!thread.isAlive()) {
            thread.start();
        }
        settings = getSharedPreferences(PHYSICIAN_PREFS, MODE_PRIVATE);
        editor = settings.edit();
        runningMonitorIds = new HashSet<>();
    }

    @Override
    public void onDestroy() {
        unregisterMetrics();
        unbindService(mConnection);
        serviceConnected = false;
        super.onDestroy();
    }

    private void registerMetrics() {
        String[] tokens;
        for (String rm : runningMetrics) {
            tokens = rm.split("\\|");
            try {
                int monitorId = mCimonInterface.registerPeriodic(Integer.parseInt(tokens[0]), Long.parseLong(tokens[1]), Long.parseLong(tokens[2]), true, null);
                runningMonitorIds.add(tokens[0] + "|" + Integer.toString(monitorId));
                if (Metrics.BLUETOOTH_DEVICE == Integer.parseInt(tokens[0])) {
                    registerBluetooth();
                }
                if (DebugLog.DEBUG) Log.d(TAG, "PhysicianService.registerMetrics: registering metric: " + tokens[0]);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        editor.putStringSet(RUNNING_MONITOR_IDS, runningMonitorIds);
        editor.commit();
    }

    private void unregisterMetrics() {
        String[] tokens;
        for (String rmi : runningMonitorIds) {
            tokens = rmi.split("\\|");
            try {
                mCimonInterface.unregisterPeriodic(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
                if (Metrics.BLUETOOTH_DEVICE == Integer.parseInt(tokens[0])) {
                    unregisterBluetooth();
                }
                if (DebugLog.DEBUG) Log.d(TAG, "PhysicianService.unregisterMetrics: unregister metric: " + tokens[0] + " - monitorId: " + tokens[1]);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        editor.remove(RUNNING_MONITOR_IDS);
        editor.commit();
    }

    public class LocalBinder extends Binder {
        PhysicianService getService() {
            return PhysicianService.this;
        }
    }

    public CimonInterface getCimonInterface() {
        return this.mCimonInterface;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DebugLog.DEBUG) Log.d(TAG, "PhysicianService.onBind - bind");
        return new LocalBinder();
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DebugLog.DEBUG) Log.d(TAG, "PhysicianService.NDroidSystem.onServiceConnected - connected");
            mCimonInterface = CimonInterface.Stub.asInterface(service);
            serviceConnected = true;
            registerMetrics();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DebugLog.DEBUG) Log.d(TAG, "PhysicianService.NDroidSystem.onServiceDisconnected - disconnected");
            mCimonInterface = null;
            serviceConnected = false;
        }
    };

    /**
     * Register Bluetooth BroadcastReceiver
     */
    public void registerBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothAdapter.enable();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(BluetoothService.bluetoothReceiver, filter);
        if (DebugLog.DEBUG) Log.d(TAG, "PhysicianService.registerBluetooth - registerReceiver");
    }

    /**
     * Unregister Bluetooth BroadcastReceiver
     */
    public void unregisterBluetooth() {
        unregisterReceiver(BluetoothService.bluetoothReceiver);
        if (DebugLog.DEBUG) Log.i(TAG, "PhysicianService.unregisterBluetooth - unregisterReceiver");
    }

}
