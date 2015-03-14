package edu.nd.darts.cimon;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ningxia
 */
public class PhysicianInterface extends Activity {

    private static final String TAG = "NDroid";

    private static ListView listView;
    private static List<ActivityCategory> categories;
    private static Set<ActivityItem> allItems;
    private ArrayAdapter<ActivityCategory> listAdapter;
    private ActivityCategory location, mobility, activity, communication, wellbeing, social, everything;
    private ActivityItem gps, accelerometer, gyroscope, bluetooth, wifi;
    private static Button btnMonitor;
    private static TextView message;

    private static CimonInterface mCimonInterface = null;
//    private SparseArray<MonitorReport> monitorReports;
    private Handler backgroundHandler = null;
    private static AdminObserver adminObserver;

    public static final long PERIOD = 1000;
    public static final long DURATION = 0;                  // continuous

    public static final String PHYSICIAN_METRICS = "physician_metrics";
    private static final String CHECKED_CATEGORIES = "checked_categories";
    public static final String RUNNING_METRICS = "running_metrics";
    public static final String RUNNING_MONITOR_IDS = "running_monitor_ids";
    private static SharedPreferences settings;
    private static Set<String> checkedCategories;
    /**
     * Monitor Ids {@link edu.nd.darts.cimon.database.MonitorTable}
     */
    private static Set<String> runningMonitorIds;
    /**
     * Metrics {@link edu.nd.darts.cimon.Metrics}
     */
    private static Set<String> runningMetrics;

    private BluetoothAdapter mBluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_physician_interface);

        listView = (ListView) findViewById(R.id.physician_listView);

        // load category list
        loadCategoryList();

        listAdapter = new ActivityArrayAdapter(this, R.layout.physician_item, categories);
        listView.setAdapter(listAdapter);

        btnMonitor = (Button) findViewById(R.id.physician_monitor_btn);
        btnMonitor.setOnClickListener(btnMonitorHandler);
        message = (TextView) findViewById(R.id.physician_message);

//        startService(new Intent(this, NDroidService.class));
        adminObserver = SensorObserver.getInstance();

        Intent intent = new Intent(NDroidService.class.getName());
        intent.setPackage("edu.nd.darts.cimon");
        if(getApplicationContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            if (DebugLog.DEBUG) Log.d(TAG, "PhysicianInterface.onCreate - bind service.");
        }
        else {
            if (DebugLog.DEBUG) Log.d(TAG, "PhysicianInterface.onCreate - bind service failed.");
        }

//        backgroundThread.start();

        settings = getSharedPreferences(PHYSICIAN_METRICS, MODE_PRIVATE);
        if (ifPreference()) {
            resumeStatus();
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

//    private HandlerThread backgroundThread = new HandlerThread("physicianinterface") {
//        @Override
//        protected void onLooperPrepared() {
//            backgroundHandler = new Handler(getMainLooper());
//            super.onLooperPrepared();
//        }
//    };

    /**
     * Class for interacting with the main interface of the service.
     * On connection, acquire binder to {@link CimonInterface} from the
     * CIMON background service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            if (DebugLog.DEBUG) Log.d(TAG, "PhysicianInterface.NDroidSystem.onServiceConnected - connected");
            mCimonInterface = CimonInterface.Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            if (DebugLog.DEBUG) Log.d(TAG, "PhysicianInterface.NDroidSystem.onServiceDisconnected - disconnected");
            mCimonInterface = null;

        }
    };

    /**
     * Set preference
     * @param bool boolean value indicating set or clear preferences
     */
    public void setPreference(boolean bool) {
        if (DebugLog.DEBUG) {
            Log.d(TAG, "Physician.setPreference - preferences set " + bool);
        }
        SharedPreferences.Editor editor = settings.edit();

        if (bool) {
            checkedCategories = new HashSet<>();
            for (ActivityCategory ac : categories) {
                if (ac.isChecked()) {
                    checkedCategories.add(ac.getTitle());
                }
            }
            editor.putStringSet(CHECKED_CATEGORIES, checkedCategories);
            editor.putStringSet(RUNNING_MONITOR_IDS, runningMonitorIds);
            editor.putStringSet(RUNNING_METRICS, runningMetrics);
        }
        else {
            editor.clear();
        }

        editor.commit();
    }

    private void resumeStatus() {
        if (ifPreference()) {
            Toast.makeText(this, "Monitors are running...", Toast.LENGTH_LONG).show();
            for (ActivityCategory ac : categories) {
                if (checkedCategories.contains(ac.getTitle())) {
                    ac.setChecked(true);
                }
            }
            btnMonitor.setText("Stop");
            btnMonitor.setEnabled(true);
            message.setText("Running...");
            message.setVisibility(View.VISIBLE);
        }
    }

    public static boolean ifPreference() {
        checkedCategories = settings.getStringSet(CHECKED_CATEGORIES, null);
        if (checkedCategories != null) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Register a new periodic update when metric is selected via ActivityCategory.
     * @param metric    integer representing metric (per {@link Metrics}) to register
     * @param period    period between updates, in milliseconds
     * @param duration  duration to run monitor, in milliseconds
     * @return  unique id of registered monitor, -1 on failure (typically because metric is not supported on this system)
     */
    public static int registerPeriodic(int metric, long period, long duration) {
        if (DebugLog.DEBUG) Log.d(TAG, "PhysicianInterface.registerPeriodic - metric:" + metric + " period:" +
                period + " duration:" + duration);
        int monitorId = -1;
        if (mCimonInterface == null) {
            if (DebugLog.INFO) Log.i(TAG, "PhysicianInterface.registerPeriodic - register: service inactive");
        }
        else {
            try {
                if (!adminObserver.getStatus(metric)) {
                    monitorId = mCimonInterface.registerPeriodic(
                            metric, period, duration, false, null);	//mMessenger
                    adminObserver.setActive(metric, monitorId);
//                    monitorReports.append(monitorId,
//                            new MonitorReport(this, metric, monitorId, backgroundHandler, adminObserver,
//                                    true, true, false, false, false));
                }
            } catch (RemoteException e) {
                if (DebugLog.INFO) Log.i(TAG, "PhysicianInterface.registerPeriodic - register failed");
                e.printStackTrace();
            }
        }
        return monitorId;
    }

    /**
     * Unregister periodic update monitor which was registered through administration activity.
     * This method is called by the onCheckedChanged listener for the Enable button
     * of the administration rows when the state is changed to disable.
     *
     * @param metric    integer representing metric (per {@link Metrics}) to unregister
     */
    public void unregisterPeriodic(int metric) {
        if (DebugLog.DEBUG) Log.d(TAG, "PhysicianInterface.OnClickListener - unregister periodic");
        if (mCimonInterface != null) {
            try {
                int monitorId = adminObserver.getMonitor(metric);
                if (monitorId >= 0) {
                    mCimonInterface.unregisterPeriodic(metric, monitorId);
                    adminObserver.setInactive(metric, monitorId);
                }
            } catch (RemoteException e) {
                if (DebugLog.INFO) Log.i(TAG, "PhysicianInterface.unregisterPeriodic - unregister failed");
                e.printStackTrace();
            }
        }
    }

    /**
     * Monitor button OnClickListener
     */
    View.OnClickListener btnMonitorHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Button btn = (Button) v;

            if (btn.getText().toString().equalsIgnoreCase("Monitor")) {
                btn.setText("Stop");
                enableCheckbox(false);
                monitorManager(true);
                message.setText("Running...");
                message.setVisibility(View.VISIBLE);
            }
            else {
                btn.setText("Monitor");
                enableCheckbox(true);
                monitorManager(false);
                message.setVisibility(View.GONE);
            }
        }
    };

    /**
     * Manage multiple monitors registering
     * @param register register multiple monitors or not
     */
    private void monitorManager(boolean register) {
        int monitorId;
        runningMonitorIds = new HashSet<>();
        runningMetrics = new HashSet<>();
        for (ActivityItem ai : allItems) {
            if (ai.getSelected()) {
                for (int i = ai.getGroupId(); i < ai.getGroupId() + ai.getMembers(); i ++) {
                    if (DebugLog.DEBUG) {
                        Log.d(TAG, "PhysicianInterface.monitorManager - metric: " + i);
                    }
                    if (register) {
                        monitorId = PhysicianInterface.this.registerPeriodic(i, ai.getPeriod(), DURATION);
                        if (DebugLog.DEBUG) {
                            Log.d(TAG, "PhysicianInterface.monitorManager - monitorId: " + monitorId);
                        }
                        runningMonitorIds.add(Integer.toString(monitorId));
                        runningMetrics.add(Integer.toString(i));
                    }
                    else {
                        PhysicianInterface.this.unregisterPeriodic(i);
                    }
                }
            }
        }

        // set or clear the SharedPreference accordingly
        setPreference(register);
    }

    /**
     * Manage accessibility of CheckBoxes and TextViews
     * @param bool enable or disable
     */
    private void enableCheckbox(boolean bool) {
        if (DebugLog.DEBUG) {
            Log.d(TAG, "PhysicianInterface.enableCheckbox - " + bool);
        }
        ListView lv = (ListView) findViewById(R.id.physician_listView);
        for (int i = 0; i < lv.getCount(); i ++) {
            RelativeLayout rl = (RelativeLayout) lv.getChildAt(i);
            CheckBox cb = (CheckBox) rl.findViewById(R.id.physician_item_checkBox);
            TextView tv = (TextView) rl.findViewById(R.id.physician_item_textView);
            cb.setEnabled(bool);
            tv.setEnabled(bool);
        }
    }

    private static boolean isChecked() {
        for (ActivityCategory ac : categories) {
            if (ac.isChecked()) {
                return true;
            }
        }
        return false;
    }

    private static boolean nonChecked() {
        for (ActivityCategory ac : categories) {
            if (ac.isChecked()) {
                return false;
            }
        }
        return true;
    }


    private void loadCategoryList() {

        gps = new ActivityItem("GPS", Metrics.LOCATION_CATEGORY, 3);
        accelerometer = new ActivityItem("Acceler" +
                "ometer", Metrics.ACCELEROMETER, 4);
        gyroscope = new ActivityItem("Gyroscope", Metrics.GYROSCOPE, 4);
        bluetooth = new ActivityItem("Bluetooth", Metrics.BLUETOOTH_CATEGORY, 1, 60000);     // with 1 minute period
        wifi = new ActivityItem("Wifi", Metrics.WIFI_CATEGORY, 1);

        location = new ActivityCategory(
                "Location",
                new ArrayList<>(Arrays.asList(
                        gps
                ))
        );

        mobility = new ActivityCategory(
                "Mobility",
                new ArrayList<>(Arrays.asList(
                        gps, accelerometer, gyroscope
                ))
        );

        activity = new ActivityCategory(
                "Activity",
                new ArrayList<>(Arrays.asList(
                        accelerometer, gyroscope
                ))
        );

        communication = new ActivityCategory(
                "Communication",
                new ArrayList<>(Arrays.asList(
                        bluetooth, wifi
                ))
        );

        everything = new ActivityCategory(
                "Everything",
                new ArrayList<>(Arrays.asList(
                        gps, accelerometer, gyroscope
                ))
        );

        gps.addCategory(location);
        gps.addCategory(mobility);
        gps.addCategory(everything);

        accelerometer.addCategory(mobility);
        accelerometer.addCategory(activity);
        accelerometer.addCategory(everything);

        gyroscope.addCategory(mobility);
        gyroscope.addCategory(activity);
        gyroscope.addCategory(everything);

        bluetooth.addCategory(communication);
        wifi.addCategory(communication);

        categories = new ArrayList<>(Arrays.asList(
                location, mobility, activity, communication, everything
        ));

        allItems = new LinkedHashSet<>();
        allItems.addAll(location.getItems());
        allItems.addAll(mobility.getItems());
        allItems.addAll(activity.getItems());
        allItems.addAll(communication.getItems());
        allItems.addAll(everything.getItems());

    }


    private static class ActivityCategory {

        private String title;
        private boolean checked;
        private List<ActivityItem> items;

        public ActivityCategory(String title, List<ActivityItem> items) {
            this.title = title;
            this.checked = false;
            this.items = items;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
            for(ActivityItem item : items) {
                item.setSelected(checked);
            }
        }

        public List<ActivityItem> getItems() {
            return items;
        }

        public String toString() {
            return title;
        }

        public String getActivitiesString() {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            for(ActivityItem item : items) {
                sb.append(item.toString());
                sb.append(", ");
            }
            sb.replace(sb.length() - 2, sb.length(), "");
            sb.append(")");
            return sb.toString();
        }

    }


    private static class ActivityItem {

        private String title;
        private int groupId;
        private int members;
        private boolean selected;
        private long period = PERIOD;
        private List<ActivityCategory> categories = new ArrayList<>();

        public ActivityItem(String title, int groupId, int members) {
            this.title = title;
            this.groupId = groupId;
            this.members = members;
            this.selected = false;
            this.period = PERIOD;
        }

        public ActivityItem(String title, int groupId, int members, long period) {
            this(title, groupId, members);
            this.period = period;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        public int getGroupId() {
            return groupId;
        }

        public void setGroupId(int groupId) {
            this.groupId = groupId;
        }

        public int getMembers() {
            return members;
        }

        public void setMembers(int members) {
            this.members = members;
        }

        public boolean isSelected() {
            boolean bool = false;
            for (ActivityCategory ac : categories) {
                if (ac.isChecked()) {
                    bool = true;
                }
            }
            return bool;
        }

        public void setSelected(boolean setSelected) {
            // set selected to false only when all ActivityCategory are not checked
            if (!setSelected) {
                if (!isSelected()) {
                    this.selected = setSelected;
                }
            }
            // set selected to true
            else {
                this.selected = setSelected;
            }
        }

        public boolean getSelected() {
            return this.selected;
        }

        public void setPeriod(long period) {
            this.period = period;
        }

        public long getPeriod() {
            return period;
        }

        public void addCategory(ActivityCategory ac) {
            this.categories.add(ac);
        }

        public String toString() {
            return title;
        }
    }


    private static class ActivityHolder {
        private CheckBox checkBox;
        private TextView textView;

        public ActivityHolder(CheckBox checkBox, TextView textView) {
            this.checkBox = checkBox;
            this.textView = textView;
        }

        public CheckBox getCheckBox() {
            return checkBox;
        }

        public TextView getTextView() {
            return textView;
        }
    }


    private class ActivityArrayAdapter extends ArrayAdapter<ActivityCategory> {

        private LayoutInflater inflater;

        public ActivityArrayAdapter(Context context, int resource, List<ActivityCategory> objects) {
            super(context, resource, objects);
            inflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ActivityCategory category = this.getItem(position);

            CheckBox checkBox;
            final TextView textView;

            // create a new row view
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.physician_item, null);
                checkBox = (CheckBox) convertView.findViewById(R.id.physician_item_checkBox);
                textView = (TextView) convertView.findViewById(R.id.physician_item_textView);

                convertView.setTag(new ActivityHolder(checkBox, textView));
            }
            else {
                ActivityHolder activityHolder = (ActivityHolder) convertView.getTag();
                checkBox = activityHolder.getCheckBox();
                textView = activityHolder.getTextView();
            }

            // If checkbox is toggled, update the ActivityCategory it is tagged with.
            checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CheckBox cb = (CheckBox) v;

                    ActivityCategory ac = (ActivityCategory) cb.getTag();
                    ac.setChecked(cb.isChecked());
                    if (cb.isChecked()) {
                        textView.setText(ac.getActivitiesString());
                    }
                    else {
                        textView.setText("");
                    }

                    if (ac.isChecked() && ac.getItems().contains(bluetooth)) {
                        enableBluetooth();
                    }
                    else if (ac.getItems().contains(bluetooth)) {
                        disableBluetooth();
                    }

                    if (isChecked()) {
                        btnMonitor.setEnabled(true);
                    }

                    if (nonChecked()) {
                        btnMonitor.setEnabled(false);
                    }
                }
            });

            // Tag the CheckBox with the ActivityCategory it is displaying, so that we can access
            // the ActivityCategory in onClick() when the CheckBox is toggled.
            checkBox.setTag(category);

            // Display the ActivityCategory data.
            checkBox.setChecked(category.isChecked());
            checkBox.setText(category.toString());

            if (category.isChecked()) {
                textView.setText(category.getActivitiesString());
            }
            else {
                textView.setText("");
            }

            checkBox.setEnabled(!ifPreference());
            textView.setEnabled(!ifPreference());

            return convertView;
        }
    }


    public void enableBluetooth() {
        if (!mBluetoothAdapter.isEnabled()) {
            Log.i(TAG, "PhysicianInterface.enableBluetooth - is enabled");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(BluetoothService.bluetoothReceiver, filter);
        Log.i(TAG, "PhysicianInterface.enableBluetooth - registerReceiver");
    }

    public void disableBluetooth() {
        unregisterReceiver(BluetoothService.bluetoothReceiver);
        Log.i(TAG, "PhysicianInterface.enableBluetooth - unregisterReceiver");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT){
            if(mBluetoothAdapter.isEnabled()) {
                Toast.makeText(this, "Bluetooth is enabled.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "It is necessary to turn on Bluetooth!", Toast.LENGTH_SHORT).show();
                enableBluetooth();
            }
        }
    }

}
