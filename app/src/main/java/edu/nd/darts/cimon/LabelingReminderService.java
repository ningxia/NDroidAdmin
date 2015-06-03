/**
 * Labeling Reminder Service.
 *
 * @author Xiao(Sean) Bo
 */

package edu.nd.darts.cimon;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import java.util.Calendar;

import edu.nd.darts.cimon.database.LabelingHistory;


public class LabelingReminderService extends Service {

    private static final String TAG = "CimonReminderService";
    private static final int period = 1000 * 3600;
    private static int startHour = 14;
    private static int endHour = 22;
    private static int dailyTarget = 5;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Start reminder service");
        scheduleReminder();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Schedule Reminder.
     *
     * @author Xiao(Sean) Bo
     */
    private void scheduleReminder() {
        final Handler handler = new Handler();
        final Runnable worker = new Runnable() {
            public void run() {
                Calendar timeConverter = Calendar.getInstance();
                timeConverter.set(Calendar.HOUR_OF_DAY, startHour);
                long startTime = timeConverter.getTimeInMillis();
                timeConverter.set(Calendar.HOUR_OF_DAY, endHour);
                long endTime = timeConverter.getTimeInMillis();
                long currentTime = System.currentTimeMillis();
                int labelNum = getLabelNum();
                if (labelNum < dailyTarget && currentTime >= startTime && currentTime <= endTime) {
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    Toast.makeText(getApplicationContext(),
                            "Current labeling#: " + Integer.toString(labelNum) + " Today's Target:" + Integer.toString(dailyTarget),
                            Toast.LENGTH_SHORT).show();
                    v.vibrate(1000);
                }
                handler.postDelayed(this, period);
            }
        };
        handler.postDelayed(worker, period);
    }


    /**
     * Get today's labeling number.
     *
     * @author Xiao(Sean) Bo
     *
     */
    private int getLabelNum() {
        Calendar timeConverter = Calendar.getInstance();
        timeConverter.set(Calendar.HOUR_OF_DAY, 0);
        long startTime = timeConverter.getTimeInMillis();
        if (LabelingHistory.db == null) {
            LabelingHistory.open();
        }
        Cursor cursor = LabelingHistory.db.rawQuery("SELECT * FROM " + LabelingHistory.TABLE_NAME +
                " WHERE " + LabelingHistory.COLUMN_START + " >= " + Long.toString(startTime) + ";", null);
        return cursor.getCount();
    }
}
