package edu.nd.darts.cimon;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author ningxia
 */
public class PhysicianInterface extends Activity {

    private static final String TAG = "NDroid";

    private static ListView listView;
    private static List<ActivityCategory> categories;
    private ArrayAdapter<ActivityCategory> listAdapter;

    private ActivityCategory location, mobility, activity, communication, wellbeing, social, everything;

    private ActivityItem gps, accelerometer, gyroscope;

    private static Button btnMonitor;
    private static TextView message;


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
	}


    View.OnClickListener btnMonitorHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Button btn = (Button) v;
            if (btn.getText().toString().equalsIgnoreCase("Monitor")) {
                btn.setText("Stop");
                enableCheckbox(false);

                if (DebugLog.DEBUG) {
                    Log.d(TAG, "PhysicianInterface.btnMonitorHandler - display selected activities ");
                    StringBuffer sb = new StringBuffer();
                    for(ActivityCategory ac : categories) {
                        for(ActivityItem ai : ac.getItems()) {
                            sb.append(ai.getTitle());
                            sb.append(" - ");
                            sb.append(ai.isSelected());
                            sb.append("\n");
                        }
                        sb.append("\n");
                    }
                    message.setText(sb.toString());
                    message.setTextAppearance(PhysicianInterface.this, android.R.style.TextAppearance_Small);
                    message.setVisibility(View.VISIBLE);
                }
            }
            else {
                btn.setText("Monitor");
                enableCheckbox(true);
                message.setVisibility(View.GONE);
            }
        }
    };

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

        gps = new ActivityItem("GPS", Metrics.LOCATION_CATEGORY);
        accelerometer = new ActivityItem("Accelerometer", Metrics.ACCELEROMETER);
        gyroscope = new ActivityItem("Gyroscope", Metrics.GYROSCOPE);

        location = new ActivityCategory(
                "Location",
                new ArrayList<ActivityItem>(Arrays.asList(
                        gps
                ))
        );

        mobility = new ActivityCategory(
                "Mobility",
                new ArrayList<ActivityItem>(Arrays.asList(
                        gps, accelerometer, gyroscope
                ))
        );

        activity = new ActivityCategory(
                "Activity",
                new ArrayList<ActivityItem>(Arrays.asList(
                        accelerometer, gyroscope
                ))
        );

        everything = new ActivityCategory(
                "Everything",
                new ArrayList<ActivityItem>(Arrays.asList(
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

        categories = new ArrayList<ActivityCategory>(Arrays.asList(
                location, mobility, activity, everything
        ));

    }


    private static class ActivityCategory {

        private String title;
        private boolean checked;
        private List<ActivityItem> items;

        public ActivityCategory() {}

        public ActivityCategory(String title) {
            this.title = title;
        }

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

        public void setItems(List<ActivityItem> items) {
            this.items = items;
        }

        public List<ActivityItem> getItems() {
            return items;
        }

        public void addItem(ActivityItem ai){
            this.items.add(ai);
        }

        public String toString() {
            return title;
        }

        public String getActivitiesString() {
            StringBuffer sb = new StringBuffer();
            sb.append("(");
            for(ActivityItem item : items) {
                sb.append(item.toString());
                sb.append(", ");
            }
            sb.replace(sb.length() - 2, sb.length(), "");
            sb.append(")");
            return sb.toString();
        }

        public void toggleChecked() {
            setChecked(!checked);
        }
    }


    private static class ActivityItem {

        private String title;
        private int metricId;
        private boolean selected = false;
        private List<ActivityCategory> categories = new ArrayList<ActivityCategory>();

        public ActivityItem() {}

        public ActivityItem(String title, int metricId) {
            this.title = title;
            this.metricId = metricId;
            this.selected = false;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        public void setMetricId(int metricId) {
            this.metricId = metricId;
        }

        public int getMetricId() {
            return metricId;
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

        public ActivityHolder() {}

        public ActivityHolder(CheckBox checkBox, TextView textView) {
            this.checkBox = checkBox;
            this.textView = textView;
        }

        public void setCheckBox(CheckBox checkBox) {
            this.checkBox = checkBox;
        }

        public CheckBox getCheckBox() {
            return checkBox;
        }

        public void setTextView(TextView textView) {
            this.textView = textView;
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
