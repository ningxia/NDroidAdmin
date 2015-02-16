package edu.nd.darts.cimon;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private ActivityItem gps, accelerometer, gyroscope;
    private static Button btnMonitor;
    private static TextView message;

    private CimonInterface mCimonInterface = null;
    private SparseArray<MonitorReport> monitorReports;
    private Handler backgroundHandler = null;
    private AdminObserver adminObserver;

    private static long period = 1000;
    private static long duration = 10 * 1000;
    private static Map<Integer, Boolean> finishedMetrics;
    private int totalMetrics;

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

        startService(new Intent(this, NDroidService.class));
        adminObserver = SensorObserver.getInstance();

        Intent intent = new Intent(NDroidService.class.getName());
        intent.setPackage("edu.nd.darts.cimon");
        if(getApplicationContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            if (DebugLog.DEBUG) Log.d(TAG, "PhysicianInterface.onCreate - bind service.");
        }
        else {
            if (DebugLog.DEBUG) Log.d(TAG, "PhysicianInterface.onCreate - bind service failed.");
        }

        backgroundThread.start();

        monitorReports = new SparseArray<>();
        finishedMetrics = new HashMap<>();

	}

    private HandlerThread backgroundThread = new HandlerThread("physicianinterface") {
        @Override
        protected void onLooperPrepared() {
            backgroundHandler = new Handler(getMainLooper()) {
                public void handleMessage(Message msg) {
                    if(DebugLog.DEBUG) {
                        Log.d(TAG, "PhysicianInterface handleMessage: metricId " + msg.what + " is finished.");
                    }
                    finishedMetrics.put(msg.what, true);
                    if (totalMetrics == finishedMetrics.size()) {
                        btnMonitor.setText("Monitor");
                        enableCheckbox(true);
                        monitorManager(false);
                        message.setText("Monitoring Complete");
                    }
                }
            };
            super.onLooperPrepared();
        }
    };

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
     * Register a new periodic update when metric is enabled through administration activity.
     * This method is called by the onCheckedChanged listener for the Enable button
     * of the administration rows when the state is changed to enable.
     *
     * @param metric    integer representing metric (per {@link Metrics}) to register
     * @param period    period between updates, in milliseconds
     * @param duration    duration to run monitor, in milliseconds
     */
    public void registerPeriodic(int metric, long period, long duration) {
        if (DebugLog.DEBUG) Log.d(TAG, "PhysicianInterface.registerPeriodic - metric:" + metric + " period:" +
                period + " duration:" + duration);
        if (mCimonInterface == null) {
            if (DebugLog.INFO) Log.i(TAG, "PhysicianInterface - register: service inactive");
        }
        else {
            try {
                if (!adminObserver.getStatus(metric)) {
                    int monitorId = mCimonInterface.registerPeriodic(
                            metric, period, duration, false, null);	//mMessenger
                    adminObserver.setActive(metric, monitorId);
                    monitorReports.append(monitorId,
                            new MonitorReport(this, metric, monitorId, backgroundHandler, adminObserver,
                                    true, true, false, false, false));
                }
            } catch (RemoteException e) {
                if (DebugLog.INFO) Log.i(TAG, "PhysicianInterface - register failed");
                e.printStackTrace();
            }
        }
    }

    /**
     * Unregister periodic update monitor which was registered through administration activity.
     * This method is called by the onCheckedChanged listener for the Enable button
     * of the administration rows when the state is changed to disable.
     *
     * @param metric    integer representing metric (per {@link Metrics}) to unregister
     */
    public void unregisterPeriodic(int metric) {
        if (DebugLog.DEBUG) Log.d(TAG, "CimonListView.OnClickListener - unregister periodic");
        if (mCimonInterface != null) {
            try {
                int monitorId = adminObserver.getMonitor(metric);
                if (monitorId >= 0) {
                    mCimonInterface.unregisterPeriodic(metric, monitorId);
                    adminObserver.setInactive(metric, monitorId);
                }
            } catch (RemoteException e) {
                if (DebugLog.INFO) Log.i(TAG, "CimonListView.OnClickListener - unregister failed");
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
     */
    private void monitorManager(boolean register) {
        totalMetrics = 0;
        for (ActivityItem ai : allItems) {
            if (ai.isSelected()) {
                for (int i = ai.getGroupId(); i < ai.getGroupId() + ai.getMembers(); i ++) {
                    finishedMetrics.put(i, false);
                    if (register) {
                        PhysicianInterface.this.registerPeriodic(i, period, duration);
                        totalMetrics ++;
                    }
                    else {
                        PhysicianInterface.this.unregisterPeriodic(i);
                    }
                }
            }
        }
    }

    private void enableCheckbox(boolean bool) {
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

        gps = new ActivityItem("GPS", Metrics.LOCATION_CATEGORY, 4);
        accelerometer = new ActivityItem("Accelerometer", Metrics.ACCELEROMETER, 4);
        gyroscope = new ActivityItem("Gyroscope", Metrics.GYROSCOPE, 4);

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

        categories = new ArrayList<>(Arrays.asList(
                location, mobility, activity, everything
        ));

        allItems = new HashSet<>();
        allItems.addAll(location.getItems());
        allItems.addAll(mobility.getItems());
        allItems.addAll(activity.getItems());
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
        private boolean selected = false;
        private List<ActivityCategory> categories = new ArrayList<>();

        public ActivityItem(String title, int groupId, int members) {
            this.title = title;
            this.groupId = groupId;
            this.members = members;
            this.selected = false;
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


    private static class ActivityArrayAdapter extends ArrayAdapter<ActivityCategory> {

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

            return convertView;
        }
    }
}
