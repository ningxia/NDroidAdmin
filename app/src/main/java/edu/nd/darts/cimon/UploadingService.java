package edu.nd.darts.cimon;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.os.AsyncTask;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.sql.RowId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Set;

import javax.crypto.NoSuchPaddingException;

import edu.nd.darts.cimon.database.CimonDatabaseAdapter;
import edu.nd.darts.cimon.database.DataCommunicator;
import edu.nd.darts.cimon.database.DataTable;
import edu.nd.darts.cimon.database.LabelingDB;
import edu.nd.darts.cimon.database.LabelingHistory;
import edu.nd.darts.cimon.database.MetricInfoTable;
import edu.nd.darts.cimon.database.MetricsTable;

/**
 * Upadloing Service For Data
 *
 * @author Xiao(Sean) Bo
 *
 */
public class UploadingService extends Service {
    private static final String TAG = "CimonUploadingService";
    private static final String[] uploadTables = {MetricInfoTable.TABLE_METRICINFO, LabelingHistory.TABLE_NAME, MetricsTable.TABLE_METRICS, DataTable.TABLE_DATA};
    //private static final String[] uploadTables = {MetricsTable.TABLE_METRICS,MetricInfoTable.TABLE_METRICINFO};
    private static final int period = 1000 * 10;
    private static int count;
    private static int MAXRECORDS = 3000;
    private static int curWindow = 5 * MAXRECORDS;
    private static int startHour = 0;
    private static int endHour = 8;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        scheduleUploading();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        count = 0;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    private void scheduleUploading() {
        final Handler handler = new Handler();
        final Runnable worker = new Runnable() {
            public void run() {
                Log.d(TAG, "Uploading thread:" + Integer.toString(count) + "\n Time window:"
                        + Integer.toString(startHour) + "~" + Integer.toString(endHour));
                if (count < 1) {
                    runUpload();
                }
                handler.postDelayed(this, period);
            }
        };
        handler.postDelayed(worker, period);
    }

    /**
     * One iteration of upload.
     *
     * @author Xiao(Sean) Bo
     */
    private void runUpload() {
        Calendar timeConverter = Calendar.getInstance();
        timeConverter.set(Calendar.HOUR_OF_DAY, startHour);
        long startTime = timeConverter.getTimeInMillis();
        timeConverter.set(Calendar.HOUR_OF_DAY, endHour);
        long endTime = timeConverter.getTimeInMillis();
        long currentTime = System.currentTimeMillis();
        Log.d(TAG, "curTime:" + Long.toString(currentTime));
        if (currentTime >= startTime && currentTime <= endTime
                && CimonDatabaseAdapter.database != null) {
            count++;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        for (String table : uploadTables) {
                            Log.d(TAG, "Upload: " + table);
                            uploadFromTable(table);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }finally {
                        count--;
                    }
                }
            }).start();
        }
    }


    /**
     * Update from certain table.
     *
     * @param tableName table to update
     * @author Xiao(Sean) Bo
     *
     */
    private void uploadFromTable(String tableName) {
        Cursor cursor = this.getCursor(tableName);
        while (cursor.getCount() > 0) {
            //Update cursor
            try {
                cursor.moveToFirst();
                uploadCursor(cursor, tableName);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //Get new cursor
            if (tableName.equals(MetricInfoTable.TABLE_METRICINFO) || tableName.equals(MetricsTable.TABLE_METRICS))
                break;
            cursor = this.getCursor(tableName);
        }
        cursor.close();
    }

    /**
     * Update from each cursor due to cursor has size limit.
     *
     * @param tableName table to update
     * @param cursor    table to update
     * @author Xiao(Sean) Bo
     */
    private void uploadCursor(Cursor cursor, String tableName) throws JSONException,
            MalformedURLException, NoSuchAlgorithmException, NoSuchPaddingException {
        JSONArray records = new JSONArray();
        String[] columnNames = cursor.getColumnNames();
        ArrayList<Integer> rowIDs = new ArrayList<Integer>();
        while (!cursor.isAfterLast()) {
            JSONObject record = new JSONObject();
            rowIDs.add(new Integer(cursor.getInt(0)));
            for (String columnName : columnNames) {
                if (columnName.equals("_id") && (!tableName.equals(MetricInfoTable.TABLE_METRICINFO) && !
                        tableName.equals(MetricsTable.TABLE_METRICS)))
                    continue;
                int columnIndex = cursor.getColumnIndex(columnName);
                switch (cursor.getType(columnIndex)) {
                    case Cursor.FIELD_TYPE_STRING:
                        record.put(columnName, cursor.getString(columnIndex));
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        record.put(columnName,
                                Long.toString(cursor.getLong(columnIndex)));
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        record.put(columnName,
                                Float.toString(cursor.getFloat(columnIndex)));
                        break;
                    default:
                        record.put(columnName, "");
                        break;
                }
            }
            records.put(record);
            if (records.length() >= this.MAXRECORDS) {
                batchUpload(records, tableName, rowIDs);
                records = new JSONArray();
            }
            cursor.moveToNext();
        }
        if (records.length() > 0) {
            batchUpload(records, tableName, rowIDs);
        }
        cursor.close();
    }

    /**
     * Upload batch data to server.
     *
     * @param records   Array of JSON
     * @param tableName table to update
     * @param rowIDs    Corresponding row IDs of JSON
     * @author Xiao(Sean) Bo
     */

    private void batchUpload(JSONArray records, String tableName,
                             ArrayList<Integer> rowIDs) throws MalformedURLException,
            JSONException, NoSuchAlgorithmException, NoSuchPaddingException {
        DataCommunicator comm = new DataCommunicator();
        JSONObject mainPackage = new JSONObject();
        mainPackage.put("records", records);
        mainPackage.put("table", tableName);
        //mainPackage.put("table", "Test");
        String deviceID = getDeviceID();
        mainPackage.put("device_id", deviceID);
        String callBack = comm.postData(mainPackage.toString().getBytes());
        if (callBack.equals("Success")
                && (tableName.equals(DataTable.TABLE_DATA) || tableName
                .equals(LabelingHistory.TABLE_NAME))) {
            garbageCollection(rowIDs, tableName);
        }
    }

    /**
     * Upload batch data to server.
     *
     * @param tableName table to update
     * @param rowIDs    Corresponding row IDs to delete
     * @author Xiao(Sean) Bo
     */

    private static void garbageCollection(ArrayList<Integer> rowIDs,
                                          String tableName) {
        SQLiteDatabase curDB = tableName.equals(LabelingHistory.TABLE_NAME) ? LabelingHistory.db
                : CimonDatabaseAdapter.database;
        StringBuilder IDs = new StringBuilder();
        int lastIndex = rowIDs.size() - 1;
        IDs.append("(");
        for (int i = 0; i < lastIndex; i++) {
            IDs.append(rowIDs.get(i).toString() + ",");
        }
        IDs.append(rowIDs.get(lastIndex).toString() + ")");
        curDB.delete(tableName, "_id in " + IDs.toString(), null);
        rowIDs.clear();
    }

    /**
     * Get device ID.
     *
     * @author Xiao(Sean) Bo
     */

    private String getDeviceID() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getDeviceId();
    }

    /**
     * Get top 9000 records from db.
     *
     * @author Xiao(Sean) Bo
     */

    private static Cursor getCursor(String tableName) {
        if (tableName.equals(LabelingHistory.TABLE_NAME)) {
            if (LabelingHistory.db == null) {
                LabelingHistory.open();
            }
            return LabelingHistory.db.rawQuery("SELECT * FROM " + tableName + " LIMIT " + Integer.toString(curWindow) + ";",
                    null);
        } else
            return CimonDatabaseAdapter.database.rawQuery("SELECT * FROM "
                    + tableName + " LIMIT " + Integer.toString(curWindow) + ";", null);

    }

    /**
     * Send test information to server
     *
     * @author Xiao(Sean) Bo
     */
    private static void sendMsg(String msg, String deviceID) {
        try {
            DataCommunicator comm = new DataCommunicator();
            JSONObject data = new JSONObject();
            data.put("table", "test");
            data.put("info", msg);
            data.put("id", deviceID);
            comm.postData(data.toString().getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
