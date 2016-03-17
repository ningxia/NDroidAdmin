package edu.nd.darts.cimon;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.YAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.Calendar;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;

import edu.nd.darts.cimon.database.CimonDatabaseAdapter;


/**
 * Compliance Interface
 *
 * @author ningxia
 */
public class ComplianceInterface extends Activity {

    private static final String TAG = "NDroid";

    protected BarChart mChart;
    private TextView tvX, tvY;

    private Typeface mTf;
    private String[] mDates;
    private long[] mTimestamps;

//    private CimonDatabaseAdapter mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_compliance_interface);

//        mDb = CimonDatabaseAdapter.getInstance(this);

        mChart = (BarChart) findViewById(R.id.compliance_chart);
        mChart.setBackgroundColor(Color.WHITE);
        mChart.setDrawBarShadow(false);
        mChart.setDrawValueAboveBar(true);
        mChart.setDescription("");
        mChart.setDescription("");
        mChart.setPinchZoom(false);
        mChart.setDrawGridBackground(false);
        mTf = Typeface.createFromAsset(getAssets(), "OpenSans-Regular.ttf");

        XAxis xAxis = mChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTypeface(mTf);
        xAxis.setDrawGridLines(false);
        xAxis.setSpaceBetweenLabels(2);
        YAxisValueFormatter custom = new MyYAxisValueFormatter();

        YAxis yAxis = mChart.getAxisLeft();
        yAxis.setTypeface(mTf);
        yAxis.setDrawGridLines(false);
        yAxis.setValueFormatter(custom);
        yAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        yAxis.setSpaceTop(15f);

        setData(5);
        if (DebugLog.DEBUG)
            Log.d(TAG, "ComplianceInterface.onCreate: success");

    }

    private void setDates(int numberOfDays) {
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(System.currentTimeMillis());
        mDates = new String[numberOfDays];
        mTimestamps = new long[numberOfDays + 1];
        mTimestamps[numberOfDays] = cal.getTimeInMillis();
        for (int i = 0; i < numberOfDays; i ++) {
            cal.add(Calendar.DATE, -1);
            String date = cal.get(Calendar.MONTH) + 1
                    + "."
                    + cal.get(Calendar.DAY_OF_MONTH)
                    + "."
                    + cal.get(Calendar.YEAR);
            mDates[numberOfDays - 1 - i] = date;
            mTimestamps[numberOfDays - 1 - i] = cal.getTimeInMillis();
            if (DebugLog.DEBUG)
                Log.d(TAG, "ComplianceDate: " + cal.getTimeInMillis() + " - " + date);
        }
    }

    private void setData(int count) {
        if (DebugLog.DEBUG)
            Log.d(TAG, "ComplianceInterface.setData: success");

        setDates(count);

        ArrayList<String> xVals = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            xVals.add(mDates[i]);
        }

        ArrayList<BarEntry> yVals = new ArrayList<>();

        for (int i = 0; i < count; i ++) {
            if (DebugLog.DEBUG)
                Log.d(TAG, "ComplianceTimestamp: " + mTimestamps[i] + " - " + mTimestamps[i + 1]);
            yVals.add(new BarEntry(CimonDatabaseAdapter.getPercentage(mTimestamps[i], mTimestamps[i + 1]), i));
        }

        // Demo
//        for (int i = 0; i < count; i++) {
//            float mult = (range + 1);
//            float val = (float) (Math.random() * mult);
//            yVals.add(new BarEntry(val, i));
//        }

        BarDataSet set1 = new BarDataSet(yVals, "Recent Compliance Percentage");
        set1.setColors(ColorTemplate.JOYFUL_COLORS);
        set1.setBarSpacePercent(35f);

        ArrayList<IBarDataSet> dataSets = new ArrayList<>();
        dataSets.add(set1);

        BarData data = new BarData(xVals, dataSets);
        data.setValueTextSize(14f);
        data.setValueTypeface(mTf);

        mChart.setData(data);
    }

    private class MyYAxisValueFormatter implements YAxisValueFormatter {

        private DecimalFormat mFormat;

        public MyYAxisValueFormatter() {
            mFormat = new DecimalFormat("###,###,###,##0.0");
        }

        @Override
        public String getFormattedValue(float value, YAxis yAxis) {
            return mFormat.format(value) + " %";
        }
    }
}
