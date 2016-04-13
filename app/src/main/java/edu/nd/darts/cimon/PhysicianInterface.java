package edu.nd.darts.cimon;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import edu.nd.darts.cimon.database.CimonDatabaseAdapter;
import edu.nd.darts.cimon.database.ComplianceTable;
import edu.nd.darts.cimon.database.DataCommunicator;
import edu.nd.darts.cimon.database.MetricInfoTable;


/**
 * Physician Interface
 *
 * @author ningxia
 */
public class PhysicianInterface extends Activity{

    private static final String TAG = "NDroid";
    private static final String PACKAGE_NAME = "edu.nd.darts.cimon";
    public static final int REQUEST_CODE = 1002;

    private static ListView listView;
    private static List<ActivityCategory> categories;
    private static Set<ActivityItem> allItems;
    private ArrayAdapter<ActivityCategory> listAdapter;
    private ActivityCategory mobility, activity, social, wellbeing, everything;
    private ActivityItem memory, cpuLoad, cpuUtil, battery, netBytes, netPackets, connectStatus, instructionCount, sdcard;
    private ActivityItem gps, accelerometer, magnetometer, gyroscope, linearAcceleration, orientation, proximity, pressure, lightSeneor, humidity, temperature;
    private ActivityItem screenState, phoneActivity, sms, mms, bluetooth, wifi, smsInfo, mmsInfo, phoneCall, callState, browserHistory, cellLocation, application;
    private static Button btnMonitor;
    private static Button btnUpload;
    private static Button btnCompliance;
    private static TextView appVersion, lastCollect, lastUpload, dataLeft, message;

    public static final long PERIOD = 1000;
    public static final long DURATION = 0;                  // continuous

    private static final String SHARED_PREFS = "CimonSharedPrefs";
    private static final String PREF_VERSION = "version";
    private static final String PHONE_NUMBER = "phone_number";
    private static final String PHYSICIAN_PREFS = "physician_prefs";
    private static final String CHECKED_CATEGORIES = "checked_categories";
    private static final String RUNNING_METRICS = "running_metrics";
    private static final String MONITOR_STARTED = "monitor_started";
    private static SharedPreferences settings, appPrefs;
    private static SharedPreferences.Editor editor, appEditor;
    private static Set<String> checkedCategories;

    public static Intent sensorService;
    /**
     * Metrics {@link Metrics}
     */
    private static Set<String> runningMetrics;

    private BluetoothAdapter mBluetoothAdapter;
    private WifiManager mWifiManager;
    private static final int REQUEST_ENABLE_BT = 1;

    private static UploadingService us;
    private static PingService ps;

    private static long COLLECT_THRESHOLD = 2 * 60 * 1000;
    private static long UPLOAD_THRESHOLD = 24 * 3600 * 1000;

    private static String phoneNumber;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_physician_interface);

        listView = (ListView) findViewById(R.id.physician_listView);

        /**
         * Insert existing metric categories into
         * @see MetricInfoTable
         */
        loadMetricInfoTable();

        // load category list
        loadCategoryList();

        listAdapter = new ActivityArrayAdapter(this, R.layout.physician_item, categories);
        listView.setAdapter(listAdapter);

        btnMonitor = (Button) findViewById(R.id.physician_monitor_btn);
        btnMonitor.setOnClickListener(btnMonitorHandler);

        btnUpload = (Button) findViewById(R.id.physician_upload_btn);
        btnUpload.setOnClickListener(btnUploadHandler);

        appVersion = (TextView) findViewById(R.id.app_version);
        appVersion.setText("Current version: " + BuildConfig.VERSION_NAME);

        btnCompliance = (Button) findViewById(R.id.physician_compliance_btn);
        btnCompliance.setOnClickListener(btnComplianceHandler);

        message = (TextView) findViewById(R.id.physician_message);

        settings = getSharedPreferences(PHYSICIAN_PREFS, MODE_PRIVATE);
        editor = settings.edit();
        if (settings.getBoolean(MONITOR_STARTED, false)) {
            Log.d(TAG, "settings names: " + settings.getAll().keySet().toString());
            resumeStatus();
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        us = new UploadingService();
        ps = new PingService();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "It is necessary to keep Bluetooth on! Now enabling...", Toast.LENGTH_LONG).show();
            mBluetoothAdapter.enable();
        }

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (!mWifiManager.isWifiEnabled()) {
            Toast.makeText(this, "Please connect your device to proper WiFi network.", Toast.LENGTH_LONG).show();
            mWifiManager.setWifiEnabled(true);
        }

        /**
         * Refresh activity every 30 seconds.
         */
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateCompliance();
                handler.postDelayed(this, COLLECT_THRESHOLD / 4);
            }
        }, COLLECT_THRESHOLD / 4);

        appPrefs = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        appEditor = appPrefs.edit();
        phoneNumber = appPrefs.getString(PHONE_NUMBER, null);
        if (phoneNumber == null) {
            alertPhoneNumber();
        }
        else {
            new RetrieveVersionTask().execute();
        }

    }

    private void alertPhoneNumber() {
        new AlertDialog.Builder(PhysicianInterface.this)    // it has to be "PhysicianInterface.this" as context in dialog
                .setTitle("Phone Number Required")
                .setMessage("Please input your phone number in menu!")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        intent.setClass(PhysicianInterface.this, CimonPreferenceActivity.class);
                        startActivityForResult(intent, REQUEST_CODE);
                    }
                })
                .show();
    }

    private static JSONObject getJSON() {
        JSONObject jsonRequest = new JSONObject();
        try {
            jsonRequest.put("table", "Version");
            jsonRequest.put("device_id", phoneNumber);
            Log.d(TAG, "JsonRequest: " + jsonRequest.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonRequest;
    }

    private class RetrieveVersionTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... params) {
            String color;
            int versionCode = -1;
            try {
                DataCommunicator comm = new DataCommunicator();
                versionCode = comm.getVersionCode(getJSON().toString());
                if(DebugLog.DEBUG)
                    Log.d(TAG, "VersionCode: " + versionCode);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            if (versionCode > BuildConfig.VERSION_CODE) {
                color = "red";
            }
            else {
                color = "white";
            }
            return color;
        }

        @Override
        protected void onPostExecute(String color) {
            appVersion.setText(Html.fromHtml("Current version: " + "<font color=" + color + ">" + BuildConfig.VERSION_NAME + "</font>"));
        }
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
     *
     * @param bool boolean value indicating set or clear preferences
     */
    public void setPreference(boolean bool) {
        if (DebugLog.DEBUG) {
            Log.d(TAG, "PhysicianInterface.setPreference - preferences set " + bool);
        }

        if (bool) {
            checkedCategories = new HashSet();
            for (ActivityCategory ac : categories) {
                checkedCategories.add(ac.getTitle());
            }
            editor.putStringSet(CHECKED_CATEGORIES, checkedCategories);
            editor.putStringSet(RUNNING_METRICS, runningMetrics);
        } else {
            editor.clear();
        }

        editor.commit();
    }

    /**
     * Resume previous status
     */
    private void resumeStatus() {
        if (settings.getBoolean(MONITOR_STARTED, false)) {
            Toast.makeText(this, "Monitors are running...", Toast.LENGTH_LONG).show();
            checkedCategories = settings.getStringSet(CHECKED_CATEGORIES, null);
            for (ActivityCategory ac : categories) {
                if (checkedCategories != null && checkedCategories.contains(ac.getTitle())) {
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
     *
     * @return boolean
     */
    public static boolean ifPreference() {
        checkedCategories = settings.getStringSet(CHECKED_CATEGORIES, null);
        if (checkedCategories != null) {
            return true;
        } else {
            return false;
        }
    }

    private View.OnClickListener btnComplianceHandler = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            Intent i = new Intent(PhysicianInterface.this, ComplianceInterface.class);
            startActivity(i);
            updateCompliance();
        }
    };


    private void updateCompliance() {
        if (DebugLog.DEBUG)
            Log.d(TAG, "PhysicianInterface.updateCompliance()");
        String timeString;
        String color;
        lastCollect = (TextView) findViewById(R.id.last_collect);
        long lastSuccessCollectTimestamp = CimonDatabaseAdapter.getLastCompliance(ComplianceTable.TYPE_COLLECTED, ComplianceTable.COLUMN_TIMESTAMP);
        timeString = formatTime(lastSuccessCollectTimestamp);
        color = "white";
        if (settings.getStringSet(RUNNING_METRICS, null) != null &&
                (System.currentTimeMillis() - lastSuccessCollectTimestamp > COLLECT_THRESHOLD)) {
            color = "red";
        }
        lastCollect.setText(Html.fromHtml("Last collected: " + "<font color=" + color + ">" + timeString + "</font>"));

        lastUpload = (TextView) findViewById(R.id.last_upload);
        long lastSuccessUploadTimestamp = CimonDatabaseAdapter.getLastCompliance(ComplianceTable.TYPE_UPLOADED, ComplianceTable.COLUMN_TIMESTAMP);
        timeString = formatTime(lastSuccessUploadTimestamp);
        color = "white";
        if (settings.getStringSet(RUNNING_METRICS, null) != null &&
                (System.currentTimeMillis() - lastSuccessUploadTimestamp > UPLOAD_THRESHOLD)) {
            color = "red";
        }
        lastUpload.setText(Html.fromHtml("Last uploaded: " + "<font color=" + color + ">" + timeString + "</font>"));

        dataLeft = (TextView) findViewById(R.id.data_left);
        long dataLeftCount = CimonDatabaseAdapter.getDataLeft();
        dataLeft.setText("Data records to be uploaded: " +
                (dataLeftCount >= 0 ? Long.toString(dataLeftCount) : "N/A"));
    }

    private static String formatTime(long timestamp) {
        Calendar pingCal = Calendar.getInstance();
        String pingStr = "N/A";
        if (timestamp != 0) {
            pingCal.setTimeInMillis(timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy MMM dd HH:mm:ss");
            pingStr = sdf.format(pingCal.getTime());
        }
        return pingStr;
    }

    /**
     * Monitor button OnClickListener
     */
    View.OnClickListener btnMonitorHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Button btn = (Button) v;

            if (appPrefs.getString(PHONE_NUMBER, null) == null) {
                alertPhoneNumber();
                return;
            }

            if (btn.getText().toString().equalsIgnoreCase("Track")) {
                boolean monitorStarted = settings.getBoolean(MONITOR_STARTED, false);
                if (!monitorStarted) {
                    editor.putBoolean(MONITOR_STARTED, true);
                    editor.commit();
                }
                btn.setText("Stop");
                enableCheckbox(false);
                monitorManager(true);
                message.setText("Running...");
                message.setVisibility(View.VISIBLE);
                startPhysicianService();
                updateCompliance();
                stopService(new Intent(PhysicianInterface.this, UploadingService.class));
                stopService(new Intent(PhysicianInterface.this, PingService.class));
                startService(new Intent(PhysicianInterface.this, UploadingService.class));
                startService(new Intent(PhysicianInterface.this, PingService.class));
            } else {
                btn.setText("Track");
                enableCheckbox(true);
                monitorManager(false);
                editor.remove(MONITOR_STARTED);
                editor.remove(RUNNING_METRICS);
                editor.remove(CHECKED_CATEGORIES);
                editor.commit();
                message.setVisibility(View.GONE);
                Intent intent = new Intent(PhysicianInterface.this, PhysicianService.class);
                stopService(intent);
                updateCompliance();
            }
        }
    };

    /**
     * Upload button OnClickListener
     */
    View.OnClickListener btnUploadHandler = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            Button btn = (Button) v;
            if (us.getCount() > 0) {
                Toast.makeText(getApplicationContext(), "CIMON uploading is running, please try it again later.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getApplicationContext(), "CIMON is uploading data...", Toast.LENGTH_LONG).show();
                us.uploadForPhysician();
            }
            updateCompliance();
        }
    };

    /**
     * Manage multiple monitors registering
     *
     * @param register register multiple monitors or not
     */
    private void monitorManager(boolean register) {
        runningMetrics = new HashSet();
        for (ActivityItem ai : allItems) {
            for (int i = ai.getGroupId(); i < ai.getGroupId() + ai.getMembers(); i++) {
                if (DebugLog.DEBUG) {
                    Log.d(TAG, "PhysicianInterface.monitorManager - metric: " + i);
                }
                runningMetrics.add(Integer.toString(i) + "|" + ai.getPeriod() + "|" + DURATION);
            }
        }
        // set or clear the SharedPreference accordingly
        setPreference(register);
    }

    /**
     * Start PhysicianService
     */
    private void startPhysicianService() {
        this.startSensors();
        Intent intent = new Intent(MyApplication.getAppContext(), PhysicianService.class);
        startService(intent);
        if (DebugLog.DEBUG) Log.d(TAG, "PhysicianInterface.startPhysicianService - started");
    }

    /**
     * Manage accessibility of CheckBoxes and TextViews
     *
     * @param bool enable or disable
     */
    private void enableCheckbox(boolean bool) {
        if (DebugLog.DEBUG) {
            Log.d(TAG, "PhysicianInterface.enableCheckbox - " + bool);
        }
        ListView lv = (ListView) findViewById(R.id.physician_listView);
        for (int i = 0; i < lv.getCount(); i++) {
            RelativeLayout rl = (RelativeLayout) lv.getChildAt(i);
            CheckBox cb = (CheckBox) rl.findViewById(R.id.physician_item_checkBox);
            TextView tv = (TextView) rl.findViewById(R.id.physician_item_textView);
            cb.setEnabled(bool);
            tv.setEnabled(bool);
        }
    }

    /**
     * Test if there is any ActivityCategory is checked
     *
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
     *
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
        memory = new ActivityItem("Memory", Metrics.MEMORY_CATEGORY, 11, 60000);
        cpuLoad = new ActivityItem("CPU Load", Metrics.CPULOAD_CATEGORY, 3, 60000);
        battery = new ActivityItem("Battery", Metrics.BATTERY_CATEGORY, 6, 60000);
        netBytes = new ActivityItem("Network Bytes", Metrics.NETBYTES_CATEGORY, 4, 60000);
        netPackets = new ActivityItem("Network Packets", Metrics.NETPACKETS_CATEGORY, 4, 60000);
        connectStatus = new ActivityItem("Connectivity Status", Metrics.NETSTATUS_CATEGORY, 2, 60000);
        // Sensors
        gps = new ActivityItem("GPS", Metrics.LOCATION_CATEGORY, 3, 165000);
        // User Activity
        screenState = new ActivityItem("Screen State", Metrics.SCREEN_ON, 1, 300000);
        phoneActivity = new ActivityItem("Phone Activity", Metrics.TELEPHONY, 4, 60000);
        sms = new ActivityItem("SMS", Metrics.SMS_CATEGORY, 2, 60000);
        mms = new ActivityItem("MMS", Metrics.MMS_CATEGORY, 2, 60000);
        bluetooth = new ActivityItem("Bluetooth", Metrics.BLUETOOTH_CATEGORY, 1, 60000);
        wifi = new ActivityItem("Wifi", Metrics.WIFI_CATEGORY, 1, 120000);
        smsInfo = new ActivityItem("SMS Info", Metrics.SMS_INFO_CATEGORY, 2, 60000);
        mmsInfo = new ActivityItem("MMS Info", Metrics.MMS_INFO_CATEGORY, 2, 60000);
        callState = new ActivityItem("Call State", Metrics.CALLSTATE_CATEGORY, 1, 60000);
        browserHistory = new ActivityItem("Browser History", Metrics.BROWSER_HISTORY_CATEGORY, 1, 300000);
        cellLocation = new ActivityItem("Cell Location", Metrics.CELL_LOCATION_CATEGORY, 2, 60000);
        application = new ActivityItem("Application", Metrics.APPLICATION_CATEGORY, 1, 60000);

        everything = new ActivityCategory(
                "NetHealth",
                new ArrayList(Arrays.asList(
                        memory, cpuLoad, battery, netBytes, netPackets, connectStatus, gps,
                        screenState, phoneActivity, bluetooth, wifi, application, browserHistory, callState,
                        cellLocation, mmsInfo, smsInfo
                ))
        );

        for (ActivityItem ai : everything.getItems()) {
            ai.addCategory(everything);
        }

        categories = new ArrayList(Arrays.asList(
                everything
        ));
        allItems = new LinkedHashSet();
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
            for (ActivityItem item : items) {
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
            for (ActivityItem item : items) {
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
        private List<ActivityCategory> categories = new ArrayList();

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
            } else {
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
                        } else {
                            textView.setText(ac.getActivitiesString());
                        }
                    } else {
                        textView.setText("");
                    }

                    if (ac.isChecked() && ac.getItems().contains(bluetooth)) {
                        /* Enable Bluetooth explicitly */
                        // enableBluetooth();
                        if (!mBluetoothAdapter.isEnabled()) {
                            mBluetoothAdapter.enable();
                        }
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
                } else {
                    textView.setText(category.getActivitiesString());
                }
            } else {
                textView.setText("");
            }

            checkBox.setEnabled(!ifPreference());
            textView.setEnabled(!ifPreference());

            return convertView;
        }
    }

    public void startSensors() {
        sensorService = new Intent(MyApplication.getAppContext(), NDroidService.class);
        MyApplication.getAppContext().startService(sensorService);
    }

    public void stopSensors() {
        stopService(this.sensorService);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent();
            intent.setClass(this, CimonPreferenceActivity.class);
            startActivityForResult(intent, REQUEST_CODE);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCompliance();
    }


}
