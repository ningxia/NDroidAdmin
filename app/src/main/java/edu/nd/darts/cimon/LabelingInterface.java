package edu.nd.darts.cimon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import android.annotation.SuppressLint;
import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;

import edu.nd.darts.cimon.database.DataTable;
import edu.nd.darts.cimon.database.LabelingDB;
import edu.nd.darts.cimon.database.LabelingHistory;
import edu.nd.darts.cimon.database.MetricInfoTable;


/**
 * Labeling Interface.
 *
 * @author Xiao(Sean) Bo
 */

public class LabelingInterface extends Activity {
    Spinner workSpinner, timeIntervalSpinner;
    List<String> kineticStates = new ArrayList<String>();
    List<String> timeArray = Arrays.asList("Select time", "1 min", "10 min", "30 min",
            "60 min", "120 min", "180 min");
    private String TAG = "CimonLabelingInterface";
    Button saveButton, LoginButton, cancelButton, newItemButton,
            saveNewItemButton, discardNewItemButton,
            loginButton;
    Button MemoryButton, CimonButton;
    EditText et, et2, PinCode;
    private RadioGroup radioButtonGroup;
    private RadioButton radioButton;
    TextView tv, loginText, pinText, statusText, tv1;
    String work = "", loginCode = "";
    long startTime, endTime;
    private static LabelingHistory labelDB;
    private LabelingDB statesDB;
    private static final String[] uploadTables = {DataTable.TABLE_DATA,
            MetricInfoTable.TABLE_METRICINFO, LabelingHistory.TABLE_NAME};
    private static String[] initialStates = {"Sitting", "Sit to Stand",
            "Stand", "Standing to Sit", "Walking", "Stairs Up", "Stairs Down",
            "Biking", "Lying", "Outside", "Class", "Test", "Meeting", "Eating", "Sleeping", "Exercising", "Entertainment"};

    private static final String PHYSICIAN_PREFS = "physician_prefs";
    private static final String RUNNING_MONITOR_IDS = "running_monitor_ids";
    private static Set<String> runningMonitorIds;
    private boolean labelingStart;

    @SuppressLint("NewApi")
    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DebugLog.DEBUG)
            Log.d(TAG, "Initializing Labeling Interface");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ll);

        saveButton = (Button) findViewById(R.id.button1);
        newItemButton = (Button) findViewById(R.id.newItem);
        saveNewItemButton = (Button) findViewById(R.id.saveItemButton);
        discardNewItemButton = (Button) findViewById(R.id.discardItemButton);
        LoginButton = (Button) findViewById(R.id.button7);
        MemoryButton = (Button) findViewById(R.id.button8);
        CimonButton = (Button) findViewById(R.id.button9);

        cancelButton = (Button) findViewById(R.id.button2);
        workSpinner = (Spinner) findViewById(R.id.spinner1);
        timeIntervalSpinner = (Spinner) findViewById(R.id.spinner2);
        et = (EditText) findViewById(R.id.editText1);
        tv = (TextView) findViewById(R.id.textView10);
        tv1 = (TextView) findViewById(R.id.textView1);
        statusText = (TextView) findViewById(R.id.statusTextView);
        showStatus();

        this.initializeTimeSpinner();

        this.labelDB = new LabelingHistory();
        this.statesDB = new LabelingDB();
        startService(new Intent(this, UploadingService.class));

        addWorkList();
        this.labelingStart = false;

        timeIntervalSpinner
                .setOnItemSelectedListener(new OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                                               View view, int position, long id) {
                        String selectedItem = timeIntervalSpinner
                                .getSelectedItem().toString();
                        if (selectedItem.equals("Select time"))
                            return;
                        int selectedTime = convertSelectedTime(selectedItem);
                        Toast.makeText(getApplicationContext(),
                                selectedItem + " selected", Toast.LENGTH_SHORT)
                                .show();
                        Handler handler = new Handler();
                        final Runnable worker = new Runnable() {
                            public void run() {
                                if (labelingStart) {
                                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                                    Toast.makeText(getApplicationContext(),
                                            "Please finish labeling",
                                            Toast.LENGTH_SHORT).show();
                                    v.vibrate(1000);
                                }
                            }
                        };
                        handler.postDelayed(worker, selectedTime);
                        Toast.makeText(getApplicationContext(), Integer.toString(selectedTime) + " " + Boolean.toString(labelingStart), Toast.LENGTH_SHORT).show();
                        initializeTimeSpinner();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // TODO Auto-generated method stub
                    }
                });

        workSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                workSpinner.setSelection(position);
                work = (String) workSpinner.getSelectedItem();
                if (!work.equals("Select work")) {
                    if (!isInitialStates(work) && saveButton.getText().equals("Start")) {
                        new AlertDialog.Builder(LabelingInterface.this)
                                .setMessage("Options for " + work)
                                .setPositiveButton("Select",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int which) {
                                                LoginButton.setEnabled(false);
                                                tv.setVisibility(tv.VISIBLE);
                                                timeIntervalSpinner
                                                        .setVisibility(timeIntervalSpinner.VISIBLE);

                                                newItemButton.setEnabled(false);
                                                startTime = System.currentTimeMillis();
                                                saveButton.setText("Stop");
                                                saveButton.setEnabled(true);
                                                cancelButton.setEnabled(true);
                                                labelingStart = true;
                                                Toast.makeText(getBaseContext(),
                                                        work + "...Selected",
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        })
                                .setNegativeButton("Delete",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int which) {
                                                for (int i = 0; i < kineticStates
                                                        .size(); i++) {
                                                    String state = kineticStates
                                                            .get(i);
                                                    if (isInitialStates(state))
                                                        continue;
                                                    if (kineticStates.get(i)
                                                            .equals(work)) {
                                                        kineticStates.remove(i);
                                                        statesDB.deleteRow(work);
                                                    }
                                                }
                                                visualizeStates();
                                            }
                                        })
                                .setIcon(android.R.drawable.ic_dialog_alert).show();
                    } else {
                        LoginButton.setEnabled(false);
                        tv.setVisibility(tv.VISIBLE);
                        timeIntervalSpinner
                                .setVisibility(timeIntervalSpinner.VISIBLE);

                        newItemButton.setEnabled(false);
                        startTime = System.currentTimeMillis();
                        saveButton.setText("Stop");
                        saveButton.setEnabled(true);
                        cancelButton.setEnabled(true);
                        labelingStart = true;
                        Toast.makeText(getBaseContext(),
                                work + "...Selected",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {

            }
        });

        saveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String curWork = (String) workSpinner.getSelectedItem();
                if (saveButton.getText().equals("Start") && !curWork.equals("Select work")) {
                    startTime = System.currentTimeMillis();
                    saveButton.setText("Stop");
                    saveButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                    newItemButton.setEnabled(false);
                    tv.setVisibility(tv.VISIBLE);
                    timeIntervalSpinner.setVisibility(tv.VISIBLE);
                    LoginButton.setEnabled(false);
                    Toast.makeText(getApplicationContext(),
                            work + "...Selected", Toast.LENGTH_SHORT).show();
                } else {
                    initialStateLook();
                    endTime = System.currentTimeMillis();
                    tv.setVisibility(tv.INVISIBLE);
                    timeIntervalSpinner
                            .setVisibility(timeIntervalSpinner.INVISIBLE);
                    LoginButton.setEnabled(true);
                    if (!curWork.equals("Select work")) {
                        labelingStart = false;
                        labelDB.insertData(work, startTime, endTime);
                    }
                    workSpinner.setSelection(0);
                }
            }
        });

        cancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                initialStateLook();
                Toast.makeText(getBaseContext(), work + "...Canceled",
                        Toast.LENGTH_SHORT).show();
            }
        });

        newItemButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                saveButton.setEnabled(false);
                cancelButton.setEnabled(false);
                workSpinner.setEnabled(false);
                et.setVisibility(et.VISIBLE);
                newItemButton.setEnabled(false);
                saveNewItemButton.setVisibility(saveNewItemButton.VISIBLE);
                discardNewItemButton
                        .setVisibility(discardNewItemButton.VISIBLE);
                LoginButton.setEnabled(false);
            }
        });

        saveNewItemButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                if (et.getText().toString().equals("")) {
                    Toast.makeText(getBaseContext(),
                            "You have'nt added anything!!!", Toast.LENGTH_LONG)
                            .show();
                } else {
                    workSpinner.setEnabled(true);
                    saveButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                    et.setVisibility(et.INVISIBLE);
                    saveNewItemButton
                            .setVisibility(saveNewItemButton.INVISIBLE);
                    saveNewItemButton
                            .setVisibility(saveNewItemButton.INVISIBLE);
                    discardNewItemButton
                            .setVisibility(discardNewItemButton.INVISIBLE);
                    LoginButton.setEnabled(false);

                    work = et.getText().toString();
                    statesDB.insertData(work);
                    kineticStates.add(work);
                    visualizeStates();
                    et.setText("");
                    initialStateLook();
                }
            }
        });

        discardNewItemButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                // TODO Auto-generated method stub
                workSpinner.setEnabled(true);
                saveButton.setEnabled(true);
                // cancelButton.setEnabled(true);
                et.setVisibility(et.INVISIBLE);
                saveNewItemButton.setVisibility(saveNewItemButton.INVISIBLE);
                discardNewItemButton
                        .setVisibility(discardNewItemButton.INVISIBLE);
                saveButton.setText("Start");
                newItemButton.setEnabled(true);
                et.setText("");
                LoginButton.setEnabled(true);

            }
        });

        LoginButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {


                LayoutInflater layoutInflater
                        = (LayoutInflater) getBaseContext()
                        .getSystemService(LAYOUT_INFLATER_SERVICE);
                final View popupView = layoutInflater.inflate(R.layout.popup, null);
                final PopupWindow popupWindow = new PopupWindow(
                        popupView,
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT,
                        true);

                Button btnDismiss = (Button) popupView.findViewById(R.id.dismiss);
                Button GoTecPysButton = (Button) popupView.findViewById(R.id.GoTecPys);
                PinCode = (EditText) popupView.findViewById(R.id.editText1);
                radioButtonGroup = (RadioGroup) popupView.findViewById(R.id.radioButton);

                btnDismiss.setOnClickListener(new Button.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // TODO Auto-generated method stub
                        popupWindow.dismiss();
                    }
                });

                popupWindow.setFocusable(true);
                popupWindow.showAsDropDown(findViewById(R.id.textAnchor), Gravity.CENTER, 0, 0);

                GoTecPysButton.setOnClickListener(new Button.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // TODO Auto-generated method stub
                        int SelectedId = radioButtonGroup.getCheckedRadioButtonId();
                        Button selectedButton = (RadioButton) popupView.findViewById(SelectedId);
                        //for Technician Interface
                        if (selectedButton.getText().equals("Technician")) {
                            if (PinCode.getText().toString().equals("tabc")) {
                                popupWindow.dismiss();
                                Intent intent = new Intent(LabelingInterface.this, TechnicianInterface.class);
                                startActivity(intent);
                            } else {
                                PinCode.setText("");
                                Toast.makeText(getApplicationContext(), "Wrong Pin Code! Please Try Again!!!", Toast.LENGTH_SHORT).show();
                            }
                        }
                        //for Physician Interface
                        else if (selectedButton.getText().equals("Physician")) {
                            if (PinCode.getText().toString().equals("pabc")) {
                                popupWindow.dismiss();
                                Intent intent = new Intent(LabelingInterface.this, PhysicianInterface.class);
                                startActivity(intent);
                            } else {
                                PinCode.setText("");
                                Toast.makeText(getApplicationContext(), "Wrong Pin Code! Please Try Again!!!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
            }
        });

        CimonButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LabelingInterface.this,
                        NDroidAdmin.class);
                intent.putExtra("State", work);
                startActivity(intent); //

            }
        });
    }

    /**
     * Display monitors' running status
     */
    private void showStatus() {
        SharedPreferences physicianPrefs = getSharedPreferences(PHYSICIAN_PREFS, Context.MODE_PRIVATE);
        runningMonitorIds = physicianPrefs.getStringSet(RUNNING_MONITOR_IDS, null);
        if (runningMonitorIds != null) {
            statusText.setVisibility(View.VISIBLE);
        } else {
            statusText.setVisibility(View.GONE);
        }
    }

    /**
     * Add all tasks to kineticStates and visualize it
     */
    private void addWorkList() {
        // TODO Auto-generated method stub
        if (this.kineticStates.size() == 0) {
            for (String state : this.initialStates) {
                this.kineticStates.add(state);
            }
            String[] DBStates = getDBStates();
            for (String state : DBStates) {
                this.kineticStates.add(state);
            }
            this.kineticStates.add(0, "Select work");
        }
        visualizeStates();
    }

    /**
     * Get all states from DB
     *
     * @return String array of added states
     */
    private String[] getDBStates() {
        Cursor cursor = statesDB.getData();
        cursor.moveToFirst();
        ArrayList<String> states = new ArrayList<String>();
        int index = cursor.getColumnIndex(statesDB.COLUMN_STATE);
        while (!cursor.isAfterLast()) {
            String state = cursor.getString(index);
            states.add(state);
            cursor.moveToNext();
        }
        String[] arr = new String[states.size()];
        return states.toArray(arr);
    }

    /**
     * Visualize all states contained in kineticStates
     */
    private void visualizeStates() {
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, kineticStates);
        dataAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        workSpinner.setAdapter(dataAdapter);
    }

    /**
     * Initialize time spinner
     */
    private void initializeTimeSpinner() {
        ArrayAdapter<String> dataAdapter2 = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, timeArray);
        dataAdapter2
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timeIntervalSpinner.setAdapter(dataAdapter2);
    }

    /**
     * Initialize labelingInterface
     */
    private void initialStateLook() {
        // TODO Auto-generated method stub
        newItemButton.setEnabled(true);
        saveButton.setText("Start");
        LoginButton.setEnabled(true);
        cancelButton.setEnabled(false);
    }

    /**
     * Convert time in time spinner
     *
     * @return time in milliseconds
     */
    private int convertSelectedTime(String selectedTime) {
        if (selectedTime.equals("Select time"))
            return 0;
        String[] arr = selectedTime.split(" ");
        int min = 1000 * 60;
        return Integer.parseInt(arr[0]) * min;
    }

    /**
     * Convert time in time spinner
     *
     * @param state:kinetic states
     * @return boolean value based on input state
     */
    private boolean isInitialStates(String state) {
        for (String initialState : initialStates) {
            if (initialState.equals(state))
                return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        showStatus();
    }
}
