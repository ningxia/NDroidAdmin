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
import android.content.pm.PackageManager;
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
 * Physician Interface
 * @author ningxia
 */
public class PhysicianInterface extends Activity {

    private static final String TAG = "NDroid";
    private static final String PACKAGE_NAME = "edu.nd.darts.cimon";

    private static ListView listView;
    private static List<ActivityCategory> categories;
    private static Set<ActivityItem> allItems;
    private ArrayAdapter<ActivityCategory> listAdapter;
    private ActivityCategory mobility, activity, social, wellbeing , everything;
    private ActivityItem memory, cpuLoad, cpuUtil, battery, netBytes, netPackets, connectStatus, instructionCount, sdcard;
    private ActivityItem gps, accelerometer, magnetometer, gyroscope, linearAcceleration, orientation, proximity, pressure, lightSeneor, humidity, temperature;
    private ActivityItem screenState, phoneActivity, sms, mms, bluetooth, wifi;
    private static Button btnMonitor;
    private static TextView message;

    public static final long PERIOD = 1000;
    public static final long DURATION = 0;                  // continuous

    private static final String SHARED_PREFS = "CimonSharedPrefs";
    private static final String PREF_VERSION = "version";
    private static final String PHYSICIAN_PREFS = "physician_prefs";
    private static final String CHECKED_CATEGORIES = "checked_categories";
    private static final String RUNNING_METRICS = "running_metrics";
    private static SharedPreferences settings;
    private static Set<String> checkedCategories;

    public static Intent sensorService;
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

        /**
         * Insert existing metric categories into
         * @see edu.nd.darts.cimon.database.MetricInfoTable
         */
        loadMetricInfoTable();

        // load category list
        loadCategoryList();

        listAdapter = new ActivityArrayAdapter(this, R.layout.physician_item, categories);
        listView.setAdapter(listAdapter);

        btnMonitor = (Button) findViewById(R.id.physician_monitor_btn);
        btnMonitor.setOnClickListener(btnMonitorHandler);
        message = (TextView) findViewById(R.id.physician_message);

        // make sure that the NDroidService is running
//        sensorService = new Intent(this, NDroidService.class);
//        startService(sensorService);
        this.startSensors();
        //startService(new Intent(this, NDroidService.class));

        settings = getSharedPreferences(PHYSICIAN_PREFS, MODE_PRIVATE);
        if (ifPreference()) {
            resumeStatus();
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private void loadMetricInfoTable() {
        SharedPreferences appPrefs = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        int storedVersion = appPrefs.getInt(PREF_VERSION, -1);
        int appVersion = -1;
        try {
            appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (DebugLog.DEBUG) Log.d(TAG, "NDroidAdmin.onCreate - appVersion:" + appVersion +
                " storedVersion:" + storedVersion);
        if (appVersion > storedVersion) {
            new Thread(new Runnable() {

                public void run() {
                    List<MetricService<?>> serviceList;
                    serviceList = MetricService.getServices(Metrics.TYPE_SYSTEM);
                    for (MetricService<?> mService : serviceList) {
                        mService.insertDatabaseEntries();
                    }
                    serviceList.clear();
                    serviceList = MetricService.getServices(Metrics.TYPE_SENSOR);
                    for (MetricService<?> mService : serviceList) {
                        mService.insertDatabaseEntries();
                    }
                    serviceList.clear();
                    serviceList = MetricService.getServices(Metrics.TYPE_USER);
                    for (MetricService<?> mService : serviceList) {
                        mService.insertDatabaseEntries();
                    }
                    serviceList.clear();
                }
            }).start();
            SharedPreferences.Editor editor = appPrefs.edit();
            editor.putInt(PREF_VERSION, appVersion);
            editor.commit();
        }
    }

    /**
     * Set preference
     * @param bool boolean value indicating set or clear preferences
     */
    public void setPreference(boolean bool) {
        if (DebugLog.DEBUG) {
            Log.d(TAG, "PhysicianInterface.setPreference - preferences set " + bool);
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
            editor.putStringSet(RUNNING_METRICS, runningMetrics);
        }
        else {
            editor.clear();
        }

        editor.commit();
    }

    /**
     * Resume previous status
     */
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

    /**
     * Check if there is SharedPreference
     * @return boolean
     */
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
                startPhysicianService();
            }
            else {
                btn.setText("Monitor");
                enableCheckbox(true);
                monitorManager(false);
                message.setVisibility(View.GONE);
                Intent intent = new Intent(PhysicianInterface.this, PhysicianService.class);
                stopService(intent);
            }
        }
    };

    /**
     * Manage multiple monitors registering
     * @param register register multiple monitors or not
     */
    private void monitorManager(boolean register) {
        runningMetrics = new HashSet<>();
        for (ActivityItem ai : allItems) {
            if (ai.getSelected()) {
                for (int i = ai.getGroupId(); i < ai.getGroupId() + ai.getMembers(); i ++) {
                    if (DebugLog.DEBUG) {
                        Log.d(TAG, "PhysicianInterface.monitorManager - metric: " + i);
                    }
                    runningMetrics.add(Integer.toString(i) + "|" + ai.getPeriod() + "|" + DURATION);
                }
            }
        }
        // set or clear the SharedPreference accordingly
        setPreference(register);
    }

    /**
     * Start PhysicianService
     */
    private void startPhysicianService() {
        Intent intent = new Intent(MyApplication.getAppContext(), PhysicianService.class);
        intent.putStringArrayListExtra(PACKAGE_NAME + "." + RUNNING_METRICS, new ArrayList<>(runningMetrics));
        startService(intent);
        if (DebugLog.DEBUG) Log.d(TAG, "PhysicianInterface.startPhysicianService - started");
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

    /**
     * Test if there is any ActivityCategory is checked
     * @return true if checked
     */
    private static boolean isChecked() {
        for (ActivityCategory ac : categories) {
            if (ac.isChecked()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test if none of ActivityCategories is checked
     * @return true if none is checked
     */
    private static boolean nonChecked() {
        for (ActivityCategory ac : categories) {
            if (ac.isChecked()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Load ActivityCategory list
     */
    private void loadCategoryList() {
        // System
        memory = new ActivityItem("Memory", Metrics.MEMORY_CATEGORY, 11, 5000);
        cpuLoad = new ActivityItem("CPU Load", Metrics.CPULOAD_CATEGORY ,3, 5000);
        cpuUtil = new ActivityItem("CPU Utility", Metrics.PROCESSOR_CATEGORY, 9, 5000);
        battery = new ActivityItem("Battery", Metrics.BATTERY_CATEGORY, 6, 5000);
        netBytes = new ActivityItem("Network Bytes", Metrics.NETBYTES_CATEGORY, 4, 5000);
        netPackets = new ActivityItem("Network Packets", Metrics.NETPACKETS_CATEGORY, 4, 5000);
        connectStatus = new ActivityItem("Connectivity Status", Metrics.NETSTATUS_CATEGORY, 2, 5000);
        instructionCount = new ActivityItem("Instruction Count", Metrics.INSTRUCTION_CNT, 1, 5000);
        sdcard = new ActivityItem("SDCard Accesses", Metrics.SDCARD_CATEGORY, 4, 5000);
        // Sensors
        gps = new ActivityItem("GPS", Metrics.LOCATION_CATEGORY, 3, 5000);
        accelerometer = new ActivityItem("Accelerometer", Metrics.ACCELEROMETER, 4, 500);
        magnetometer = new ActivityItem("Magnetometer", Metrics.MAGNETOMETER, 4, 500);
        gyroscope = new ActivityItem("Gyroscope", Metrics.GYROSCOPE, 4, 500);
        linearAcceleration = new ActivityItem("Linear Acceleration", Metrics.LINEAR_ACCEL, 4, 500);
        orientation = new ActivityItem("Orientation", Metrics.ORIENTATION, 3, 500);
        proximity = new ActivityItem("Proximity", Metrics.PROXIMITY, 1, 5000);
        pressure = new ActivityItem("Pressure", Metrics.ATMOSPHERIC_PRESSURE, 1, 5000);
        lightSeneor = new ActivityItem("Light", Metrics.LIGHT, 1, 5000);
        humidity = new ActivityItem("Humidity", Metrics.HUMIDITY, 1, 5000);
        temperature = new ActivityItem("Temperature", Metrics.TEMPERATURE, 1, 5000);
        // User Activity
        screenState = new ActivityItem("Screen State", Metrics.SCREEN_ON, 1, 300000);
        phoneActivity = new ActivityItem("Phone Activity", Metrics.TELEPHONY, 4, 5000);
        sms = new ActivityItem("SMS", Metrics.SMS_CATEGORY, 2, 5000);
        mms = new ActivityItem("MMS", Metrics.MMS_CATEGORY, 2, 5000);
        bluetooth = new ActivityItem("Bluetooth", Metrics.BLUETOOTH_CATEGORY, 1, 60000);
        wifi = new ActivityItem("Wifi", Metrics.WIFI_CATEGORY, 1, 60000);

        // Table II: Sensor Priority (High or Medium)
        mobility = new ActivityCategory(
                "Mobility",
                new ArrayList<>(Arrays.asList(
                        accelerometer, gps, gyroscope, magnetometer
                ))
        );
        for (ActivityItem ai : mobility.getItems()) {
            ai.addCategory(mobility);
        }

        activity = new ActivityCategory(
                "Activity",
                new ArrayList<>(Arrays.asList(
                        accelerometer, gps, wifi, bluetooth, gyroscope, magnetometer, proximity
                ))
        );
        for (ActivityItem ai : activity.getItems()) {
            ai.addCategory(activity);
        }

        social = new ActivityCategory(
                "Social Interaction",
                new ArrayList<>(Arrays.asList(
                        accelerometer, gps, wifi, bluetooth, gyroscope, proximity
                ))
        );
        for (ActivityItem ai: social.getItems()) {
            ai.addCategory(social);
        }

        wellbeing = new ActivityCategory(
                "Wellbeing",
                new ArrayList<>(Arrays.asList(
                    accelerometer, gps, wifi, bluetooth, gyroscope, magnetometer, proximity
                ))
        );
        for (ActivityItem ai : wellbeing.getItems()) {
            ai.addCategory(wellbeing);
        }

        everything = new ActivityCategory(
                "Everything",
                new ArrayList<>(Arrays.asList(
                        memory, cpuLoad, cpuUtil, battery, netBytes, netPackets, connectStatus, instructionCount, sdcard,
                        gps, accelerometer, magnetometer, gyroscope, linearAcceleration, orientation, proximity, pressure, lightSeneor, /*humidity, temperature,*/
                        screenState, phoneActivity, sms, mms, bluetooth, wifi
                ))
        );
        for (ActivityItem ai : everything.getItems()) {
            ai.addCategory(everything);
        }

        categories = new ArrayList<>(Arrays.asList(
                mobility, activity, social, wellbeing, everything
        ));
        allItems = new LinkedHashSet<>();
        for (ActivityCategory ac : categories) {
            allItems.addAll(ac.getItems());
        }

    }


    /**
     * ActivityCategory class for displaying activity categories
     */
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

    /**
     * ActivityItem class for each individual activity
     */
    private static class ActivityItem {

        private String title;
        private int groupId;
        private int members;
        private boolean selected;
        private long period;
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

    /**
     * Class for holding CheckBox and TextView within each item of ListView
     */
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

    /**
     * Activity ArrayAdapter for ListView
     */
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
                        if (ac.getTitle().equals(everything.getTitle())) {
                            textView.setText("");
                        }
                        else {
                            textView.setText(ac.getActivitiesString());
                        }
                    }
                    else {
                        textView.setText("");
                    }

                    if (ac.isChecked() && ac.getItems().contains(bluetooth)) {
                        /* Enable Bluetooth explicitly */
                        // enableBluetooth();
                        mBluetoothAdapter.enable();
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
                if (category.getTitle().equals(everything.getTitle())) {
                    textView.setText("");
                }
                else {
                    textView.setText(category.getActivitiesString());
                }
            }
            else {
                textView.setText("");
            }

            checkBox.setEnabled(!ifPreference());
            textView.setEnabled(!ifPreference());

            return convertView;
        }
    }

    /**
     * Enable Bluetooth explicitly
     */
    public void enableBluetooth() {
        if (!mBluetoothAdapter.isEnabled()) {
            Log.i(TAG, "PhysicianInterface.enableBluetooth - is enabled");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
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

    public void startSensors(){
        sensorService = new Intent(this, NDroidService.class);
        startService(sensorService);
    }

    public void stopSensors(){
        stopService(this.sensorService);
    }

}
